package org.tsitle.rtsp_server.config;

import com.google.gson.*;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Read configuration from a JSON file.
 */
public final class RtspSrvConfigFileReader {

	private static final String RSC_PREFIX = "rsc:";

	private RtspSrvConfigFileReader() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Read the main configuration from a JSON file.
	 * @param filename Input filename (may have 'rsc:' prefix to read from the 'resources' folder)
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	public static @NonNull RtspSrvConfigMainNg readMainConfigFromFile(@NonNull String filename)
			throws ConfigInvalidException, IOException {
		RtspSrvConfigMainNg resObj = internalReadGenericConfigFromFile(RtspSrvConfigMainNg.class, filename);
		//
		Path path = Paths.get(filename).normalize();
		resObj.validate(path.getParent().toString());
		return resObj;
	}

	/**
	 * Read a 'streams' configuration from a JSON file.
	 * @param filename Input filename (may have 'rsc:' prefix to read from the 'resources' folder)
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	public static @NonNull RtspSrvConfigFileStreamsNg readStreamsConfigFromFile(
				@NonNull RtspSrvConfigMainNg rtspConfigMain,
				@NonNull String filename
			) throws ConfigInvalidException, IOException {
		RtspSrvConfigFileStreamsNg resObj = internalReadGenericConfigFromFile(RtspSrvConfigFileStreamsNg.class, filename);
		resObj.validate(
				rtspConfigMain.getUserAccountGroupIds(),
				rtspConfigMain.getDataDirAsPath()
			);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static ExclusionStrategy buildExclusionStrategy() {
		return new ExclusionStrategy() {
				@Override
				public boolean shouldSkipClass(Class<?> clazz) {
					return false;
				}
				@Override
				public boolean shouldSkipField(FieldAttributes field) {
					return field.getAnnotation(GsonAnnoExclude.class) != null;
				}
			};
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Read a configuration from a JSON file.
	 * @param filename Input filename (may have 'rsc:' prefix to read from the 'resources' folder)
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	private static <T extends RtspSrvConfigFileBase> @NonNull T internalReadGenericConfigFromFile(
				@NonNull Class<T> configClass,
				@NonNull String filename
			) throws ConfigInvalidException, IOException {
		if (! filename.startsWith(RSC_PREFIX)) {
			Path path = Paths.get(filename).normalize();
			File file = path.toFile();
			try (InputStream is = new FileInputStream(file)) {  // throws FileNotFoundException
				return readConfigFromStream(configClass, is);
			} catch (FileNotFoundException e) {
				throw new IOException("file '" + filename + "' not found (absolute path '" + path.toAbsolutePath() + "')");
			}
		} else {
			return readConfigFromResourcesFile(configClass, filename.substring(RSC_PREFIX.length()));
		}
	}

	/**
	 * Read a configuration from a JSON stream.
	 * @param stream Input stream
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	private static <T extends RtspSrvConfigFileBase> @NonNull T readConfigFromStream(
				@NonNull Class<T> configClass,
				@NonNull InputStream stream
			) throws ConfigInvalidException, IOException {
		final String errorMsgPrefix = "Error in input stream: ";

		return internalActuallyReadConfig(configClass, errorMsgPrefix, stream);
	}

	/**
	 * Read a configuration from a JSON file in the resource folder.
	 * @param rscFilename Input filename
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	private static <T extends RtspSrvConfigFileBase> @NonNull T readConfigFromResourcesFile(
				@NonNull Class<T> configClass,
				@NonNull String rscFilename
			) throws ConfigInvalidException, IOException {
		final String errorMsgPrefix = "Error in file: '" + rscFilename + "': ";

		try (InputStream is = RtspSrvConfigFileReader.class.getClassLoader().getResourceAsStream(rscFilename)) {
			if (is == null) {
				throw new IOException("file '" + rscFilename + "' not found");
			}
			return internalActuallyReadConfig(configClass, errorMsgPrefix, is);
		}
	}

	private static <T extends RtspSrvConfigFileBase> @NonNull T internalActuallyReadConfig(
				@NonNull Class<T> configClass,
				@NonNull String errorMsgPrefix,
				@NonNull InputStream stream
			) throws ConfigInvalidException, IOException {
		try {
			GsonBuilder gsonBldr = new GsonBuilder()
					.excludeFieldsWithoutExposeAnnotation()
					.addDeserializationExclusionStrategy(buildExclusionStrategy());

			Reader reader = new InputStreamReader(stream);

			T parsedConfig = gsonBldr.create().fromJson(reader, configClass);
			if (parsedConfig == null) {
				throw new ConfigInvalidException(errorMsgPrefix + "empty JSON");
			}
			parsedConfig.postProcess();
			return parsedConfig;
		} catch (JsonSyntaxException e) {
			throw new ConfigInvalidException(errorMsgPrefix + "syntax error in JSON: " + e.getMessage());
		} catch (JsonIOException e) {
			throw new IOException(errorMsgPrefix + "error while parsing JSON: " + e.getMessage());
		} catch (JsonParseException e) {
			throw new ConfigInvalidException(errorMsgPrefix + "could not parse JSON: " + e.getMessage());
		}
	}

}
