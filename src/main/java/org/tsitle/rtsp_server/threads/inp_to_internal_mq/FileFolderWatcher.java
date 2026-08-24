package org.tsitle.rtsp_server.threads.inp_to_internal_mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.rtsp_server.threads.CancelToken;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

final class FileFolderWatcher implements AutoCloseable {

	private static final long RESCAN_DELAY_MS = 500;

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull CancelToken cancelToken;

	private final @NonNull Path root;
	private final @NonNull Set<@NonNull String> extensions;

	/** Current set of matching files. All paths are absolute and normalized. */
	private final Set<@NonNull Path> matchingFiles = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean haveMatchingFilesChanged = new AtomicBoolean(true);

	private @Nullable WatchService watchService = null;
	private final Map<@NonNull WatchKey, @NonNull Path> watchKeys = new ConcurrentHashMap<>();

	private final ScheduledExecutorService rescanExecutor =
			Executors.newSingleThreadScheduledExecutor(r -> {
				Thread thread = new Thread(r, FileFolderWatcher.class.getSimpleName() + "-Rescan");
				thread.setDaemon(true);
				return thread;
			});

	private @Nullable ScheduledFuture<?> scheduledRescan;
	private @Nullable Thread watcherThread;

	private final AtomicBoolean running = new AtomicBoolean(true);

	FileFolderWatcher(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull ProUri folderUri,
				@NonNull Set<@NonNull String> extensions
			) throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".ctor()";

		this.logMsgInterface = logMsgInterface;
		this.cancelToken = cancelToken;

		//
		this.root = Path.of(
				folderUri.getPath().orElseThrow(() -> new IllegalArgumentException(FNC_NAME + ": folderUri must be set"))
			).toAbsolutePath().normalize();

		this.extensions = new HashSet<>();

		for (String extension : extensions) {
			extension = extension.toLowerCase(Locale.ROOT);
			if (! extension.startsWith(".")) {
				extension = "." + extension;
			}
			this.extensions.add(extension);
		}

		if (! Files.isDirectory(this.root)) {
			throw new IllegalArgumentException(FNC_NAME + ": Not a directory: '" + this.root + "'");
		}

		//
		start();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Returns a stable snapshot of the current file list.
	 * @param blacklistedFilePaths Blacklisted file paths
	 * @param outputList Output for the file list
	 */
	public void getMatchingFilesAsStrings(
				@NonNull Set<@NonNull String> blacklistedFilePaths,
				@NonNull Set<@NonNull String> outputList
			) {
		haveMatchingFilesChanged.set(false);
		outputList.clear();
		for (Path path : matchingFiles) {
			String pathStr = path.toString();
			if (! blacklistedFilePaths.contains(pathStr)) {
				outputList.add(pathStr);
			}
		}
	}

	/**
	 * Check if the file list has changed.
	 */
	public boolean haveMatchingFilesChanged() {
		return haveMatchingFilesChanged.get();
	}

	/**
	 * Stop watching and release resources.
	 */
	@Override
	public void close() throws IOException {
		running.set(false);

		synchronized (this) {
			if (scheduledRescan != null) {
				scheduledRescan.cancel(false);
			}
		}

		if (watcherThread != null) {
			watcherThread.interrupt();
			watcherThread = null;
		}

		rescanExecutor.shutdownNow();

		if (watchService != null) {
			watchService.close();
			watchService = null;
		}

		watchKeys.clear();
		matchingFiles.clear();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Start watching.
	 * @throws IOException If any file I/O error occurs
	 */
	private void start() throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".start()";

		if (! running.get()) {
			throw new IllegalStateException(FNC_NAME + ": Cannot re-start");
		}

		watchService = FileSystems.getDefault().newWatchService();

		// initial setup
		registerRecursively(root);
		scanRecursively(root);

		//
		watcherThread = new Thread(this::watchLoop, getClass().getSimpleName() + "-Main");
		watcherThread.setDaemon(true);
		watcherThread.start();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Register a directory and all of its subdirectories.
	 */
	private void registerRecursively(@NonNull Path directory) throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".registerRecursively()";

		try (var stream = Files.walk(directory)) {
			stream
					.filter(p ->
							Files.isDirectory(p) || Files.isSymbolicLink(p)
						)
					.forEach(p -> {
							try {
								if (Files.isSymbolicLink(p)) {
									p = p.toRealPath().toAbsolutePath().normalize();
									if (! Files.isDirectory(p)) {
										return;
									}
								}
								registerDirectory(p);
							} catch (IOException e) {
								logError(FNC_NAME, "Could not register directory: " + p);
							}
						});
		}
	}

	/**
	 * Register a single directory.
	 */
	private void registerDirectory(@NonNull Path directory) throws IOException {
		if (watchService == null) {
			return;
		}
		WatchKey key = directory.register(
				watchService,
				ENTRY_CREATE,
				ENTRY_DELETE,
				ENTRY_MODIFY
			);
		watchKeys.put(key, directory);
	}

	/**
	 * Perform a recursive scan.
	 */
	private void scanRecursively(@NonNull Path directory) {
		final String FNC_NAME = getClass().getSimpleName() + ".scanRecursively()";

		if (! running.get() || cancelToken.cancelled) {
			return;
		}

		try {
			if (Files.isSymbolicLink(directory)) {
				directory = directory.toRealPath().toAbsolutePath().normalize();
				if (! Files.isDirectory(directory)) {
					return;
				}
			}
		} catch (IOException e) {
			logError(FNC_NAME, "Could not convert symlink to abs directory '" + directory + "': " + e.getMessage());
			return;
		}

		try (var stream = Files.walk(directory)) {
			stream
					.filter(Files::isRegularFile)
					.filter(this::matchesExtension)
					.forEach(path ->
							matchingFiles.add(
									path.toAbsolutePath().normalize()
								)
						);
		} catch (IOException e) {
			logError(FNC_NAME, "Could not scan directory '" + directory + "': " + e.getMessage());
		}
	}

	private boolean matchesExtension(@NonNull Path path) {
		String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

		for (String extension : extensions) {
			if (fileName.endsWith(extension)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Schedule a complete rescan.<br />
	 * Every call resets the timer, so a burst of filesystem events
	 * results in only ONE rescan after the filesystem has been quiet
	 * for RESCAN_DELAY_MS.
	 */
	private synchronized void scheduleRescan() {
		if (! running.get() || cancelToken.cancelled) {
			return;
		}

		if (scheduledRescan != null) {
			scheduledRescan.cancel(false);
		}

		scheduledRescan = rescanExecutor.schedule(
				this::rebuildFileList,
				RESCAN_DELAY_MS,
				TimeUnit.MILLISECONDS
			);
	}

	/**
	 * Rebuild both the directory registrations and file list.
	 */
	private synchronized void rebuildFileList() {
		final String FNC_NAME = getClass().getSimpleName() + ".rebuildFileList()";

		matchingFiles.clear();

		if (! Files.isDirectory(root)) {
			return;
		}

		/*
		 * Remove invalid WatchKeys.
		 */
		watchKeys.entrySet().removeIf(entry -> {
				WatchKey key = entry.getKey();
				return (! key.isValid());
			});

		/*
		 * Register any directories that might have appeared.
		 *
		 * Duplicates aren't a problem for correctness here because
		 * WatchService allows multiple registrations, but we can avoid
		 * unnecessary registrations by checking the existing paths.
		 */
		Set<Path> registeredDirectories = new HashSet<>(
				watchKeys.values()
			);

		try (var stream = Files.walk(root)) {
			AtomicBoolean haveIoExc = new AtomicBoolean(false);
			stream
					.filter(Files::isDirectory)
					.forEach(directory -> {
							if (! registeredDirectories.contains(directory)) {
								try {
									registerDirectory(directory);
								} catch (IOException e) {
									logError(FNC_NAME, "Could not register directory '" + directory + "': " + e.getMessage());
									haveIoExc.set(true);
								}
							}
						});
			if (haveIoExc.get()) {
				haveMatchingFilesChanged.set(true);
				return;
			}
		} catch (IOException e) {
			logError(FNC_NAME, "Could not rebuild file list: " + e.getMessage());
			haveMatchingFilesChanged.set(true);
			return;
		}

		/*
		 * Rebuild the matching file list.
		 */
		scanRecursively(root);

		//
		haveMatchingFilesChanged.set(true);
	}

	private void watchLoop() {
		final String FNC_NAME = getClass().getSimpleName() + ".watchLoop()";

		if (watchService == null) {
			return;
		}

		while (running.get() && ! cancelToken.cancelled) {
			WatchKey key;
			try {
				key = watchService.take();
			} catch (InterruptedException e) {
				if (! running.get()) {
					break;
				}
				continue;
			} catch (ClosedWatchServiceException e) {
				break;
			}

			Path directory = watchKeys.get(key);
			if (directory == null) {
				key.reset();
				continue;
			}

			boolean needsRescan = false;
			boolean haveChanges = false;
			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();

				/*
				 * If the WatchService queue overflowed, we can no longer
				 * trust the incremental state. Do a complete rescan.
				 */
				if (kind == OVERFLOW) {
					needsRescan = true;
					continue;
				}

				Path changed = directory
								.resolve((Path)event.context())
								.toAbsolutePath()
								.normalize();

				if (kind == ENTRY_CREATE) {
					if (Files.isDirectory(changed)) {
						/*
						 * A directory appeared. Register its complete
						 * subtree immediately so that changes inside it
						 * are not missed.
						 */
						try {
							registerRecursively(changed);
						} catch (IOException e) {
							logError(FNC_NAME, "Could not register new directory: " + changed);
						}

						/*
						 * The directory may already contain many files,
						 * so let the debounced full scan discover them.
						 */
						needsRescan = true;
					} else if (Files.isRegularFile(changed) && matchesExtension(changed)) {
						matchingFiles.add(changed);
						haveChanges = true;
					}
				} else if (kind == ENTRY_MODIFY) {
					/*
					 * A modification normally doesn't change the file
					 * list, but adding it here is harmless and also
					 * handles unusual file replacement behavior.
					 */
					if (Files.isRegularFile(changed) && matchesExtension(changed)) {
						matchingFiles.add(changed);
						haveChanges = true;
					}
				} else if (kind == ENTRY_DELETE) {
					/*
					 * For a normal file deletion this is enough.
					 */
					matchingFiles.remove(changed);
					haveChanges = true;

					/*
					 * We cannot reliably know all files that disappeared when an entire directory is removed.
					 * Therefore, do a debounced full rescan.
					 */
					needsRescan = true;
				}
			}

			boolean valid = key.reset();
			if (! valid) {
				watchKeys.remove(key);
				/*
				 * The directory itself was deleted.
				 */
				needsRescan = true;
			}

			if (! needsRescan && haveChanges) {
				haveMatchingFilesChanged.set(true);
			}

			if (needsRescan && ! cancelToken.cancelled) {
				scheduleRescan();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
