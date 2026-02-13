package org.tsitle.rtsp;

import com.google.gson.*;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.GsonAnnoExclude;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.ConfigInvalidException;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Read configuration from a JSON file.
 */
public class ConfigReader {

	/**
	 * Read configuration from a JSON file.
	 * @param filename Input filename (may have 'rsc:' prefix to read from the 'resources' folder)
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	public static @NonNull RtspConfig readConfigFromFile(@NonNull String filename)
			throws ConfigInvalidException, IOException {
		if (! filename.startsWith("rsc:")) {
			Path path = Paths.get(filename).normalize();
			File file = path.toFile();
			try (InputStream is = new FileInputStream(file)) {  // throws FileNotFoundException
				return readConfigFromStream(is);
			} catch (FileNotFoundException e) {
				throw new IOException("file '" + filename + "' not found (absolute path '" + path.toAbsolutePath() + "')");
			}
		} else {
			return readConfigFromResourcesFile(filename.substring(4));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static ExclusionStrategy buildExlusionStrategy() {
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
	 * Read configuration from a JSON stream.
	 * @param stream Input stream
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	private static @NonNull RtspConfig readConfigFromStream(@NonNull InputStream stream)
			throws ConfigInvalidException, IOException {
		final String errorMsgPrefix = "Error in input stream: ";

		return internalConfigRead(errorMsgPrefix, stream);
	}

	/**
	 * Read configuration from a JSON file in the resource folder.
	 * @param rscFilename Input filename
	 * @return Deserialized JSON data
	 * @throws ConfigInvalidException If the file contains invalid JSON
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	private static @NonNull RtspConfig readConfigFromResourcesFile(@NonNull String rscFilename)
			throws ConfigInvalidException, IOException {
		final String errorMsgPrefix = "Error in file: '" + rscFilename + "': ";

		try (InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream(rscFilename)) {
			if (is == null) {
				throw new IOException("file '" + rscFilename + "' not found");
			}
			return internalConfigRead(errorMsgPrefix, is);
		}
	}

	private static @NonNull RtspConfig internalConfigRead(
				@NonNull String errorMsgPrefix,
				@NonNull InputStream stream
			) throws ConfigInvalidException, IOException {
		try {
			GsonBuilder gsonBldr = new GsonBuilder()
					.excludeFieldsWithoutExposeAnnotation()
					.addDeserializationExclusionStrategy(buildExlusionStrategy());

			Reader reader = new InputStreamReader(stream);

			RtspConfig appConfig = gsonBldr.create().fromJson(reader, RtspConfig.class);
			appConfig.postProcess();
			appConfig.validate();
			return appConfig;
		} catch (JsonSyntaxException e) {
			throw new ConfigInvalidException(errorMsgPrefix + "syntax error in JSON: " + e.getMessage());
		} catch (JsonIOException e) {
			throw new IOException(errorMsgPrefix + "error while parsing JSON: " + e.getMessage());
		} catch (JsonParseException e) {
			throw new ConfigInvalidException(errorMsgPrefix + "could not parse JSON: " + e.getMessage());
		}
	}

}
