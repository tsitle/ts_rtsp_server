package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Input for an RTSP stream.
 */
public final class RtspSrvConfigStreamsSs implements Cloneable {

	/** Is this Sub-Stream Source enabled? (default: true) */
	@Expose
	private @NonNull Boolean enabled;
	/** Raw File Elementary-Stream Source */
	@Expose
	private @Nullable RtspSrvConfigStreamInputEsRawFile rawFile;
	/** Message Queue Elementary-Stream Source */
	@Expose
	private @Nullable RtspSrvConfigStreamInputEsMq mq;
	/** (Muxed) File Container Stream Source */
	@Expose
	private @Nullable RtspSrvConfigStreamInputMuxFc fileContainer;
	/** (Muxed) RTSP Stream Source */
	@Expose
	private @Nullable RtspSrvConfigStreamInputMuxRtsp rtsp;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;
	@GsonAnnoExclude
	private @NonNull RtspProtoEsSourceType internalEsSourceType;
	@GsonAnnoExclude
	private final boolean internalIsVirtual;

	@SuppressWarnings("unused")
	public RtspSrvConfigStreamsSs() {
		this(false);
	}

	public RtspSrvConfigStreamsSs(boolean internalIsVirtual) {
		this.enabled = true;
		this.rawFile = null;
		this.mq = null;
		this.fileContainer = null;
		this.rtsp = null;

		this.internalHasBeenPostProcessed = false;
		this.internalEsSourceType = RtspProtoEsSourceType.ST_ES_RAW_FILE;
		this.internalIsVirtual = internalIsVirtual;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspSrvConfigStreamsSs createVirtualSsFromDemuxedSubStream(
				@NonNull RtspProtoEsSourceType esSourceType,
				@NonNull URI msSourceUri
			) throws ConfigInvalidException {
		final String FNC_NAME = RtspSrvConfigStreamsSs.class.getSimpleName() + ".createVirtualSsFromDemuxedSubStream()";

		final String errMsgUri = buildMsSourceUriForErrorMsgs(msSourceUri);

		RtspSrvConfigStreamsSs resObj = new RtspSrvConfigStreamsSs(true);
		if (esSourceType == RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_FC) {
			resObj.fileContainer = RtspSrvConfigStreamInputMuxFc.of(msSourceUri.getPath());
		} else if (esSourceType == RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ) {
			resObj.rtsp = RtspSrvConfigStreamInputMuxRtsp.of(msSourceUri.toString());
		} else {
			throw new ConfigInvalidException(FNC_NAME + ": invalid ES Source Type " + esSourceType + " " +
					"for MS Source '" + errMsgUri + "'");
		}
		resObj.postProcess();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String buildMsSourceUriForErrorMsgs(@NonNull URI msSourceUri) {
		if (! ("http".equals(msSourceUri.getScheme()) || "https".equals(msSourceUri.getScheme()))) {
			return msSourceUri.toString();
		}
		String tmpProto = msSourceUri.getScheme();
		String tmpHost = msSourceUri.getHost();
		int tmpPort = msSourceUri.getPort();
		String tmpPath = msSourceUri.getPath();
		String tmpQuery = msSourceUri.getQuery();
		URI cleanedUp = URI.create(tmpProto + "://" + tmpHost + (tmpPort > 0 ? ":" + tmpPort : "") +
				tmpPath + (tmpQuery != null ? "?" + tmpQuery : ""));
		return cleanedUp.toString()
				.replace("http://", "rtsp://")
				.replace("https://", "rtsps://");
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean getEnabled() {
		checkPostProcessed();
		return enabled;
	}

	public @NonNull RtspProtoEsSourceType getEsSourceType() {
		checkPostProcessed();
		return internalEsSourceType;
	}

	public Optional<RtspSrvConfigStreamInputEsRawFile> getSsSourceEsRawFile() {
		checkPostProcessed();
		if (rawFile == null) {
			return Optional.empty();
		}
		return Optional.of(rawFile.clone());
	}

	public Optional<RtspSrvConfigStreamInputEsMq> getSsSourceEsMq() {
		checkPostProcessed();
		if (mq == null) {
			return Optional.empty();
		}
		return Optional.of(mq.clone());
	}

	public Optional<RtspSrvConfigStreamInputMuxFc> getSsSourceMuxFc() {
		checkPostProcessed();
		if (fileContainer == null) {
			return Optional.empty();
		}
		return Optional.of(fileContainer.clone());
	}

	public Optional<RtspSrvConfigStreamInputMuxRtsp> getSsSourceMuxRtsp() {
		checkPostProcessed();
		if (rtsp == null) {
			return Optional.empty();
		}
		return Optional.of(rtsp.clone());
	}

	@SuppressWarnings("unused")
	public boolean getIsVirtual() {
		checkPostProcessed();
		return internalIsVirtual;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamsSs clone() {
		checkPostProcessed();
		try {
			RtspSrvConfigStreamsSs clone = (RtspSrvConfigStreamsSs)super.clone();
			//noinspection ConstantValue
			clone.enabled = (enabled != null && enabled);
			if (rawFile != null) {
				clone.rawFile = rawFile.clone();
			}
			if (mq != null) {
				clone.mq = mq.clone();
			}
			if (fileContainer != null) {
				clone.fileContainer = fileContainer.clone();
			}
			if (rtsp != null) {
				clone.rtsp = rtsp.clone();
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(enabled ? 1 : 0);
			baos.write(rawFile != null ? 1 : 0);
			baos.write(mq != null ? 1 : 0);
			baos.write(fileContainer != null ? 1 : 0);
			baos.write(rtsp != null ? 1 : 0);
			if (rawFile != null) {
				baos.write(rawFile.hashSum().getBytes());
			}
			if (mq != null) {
				baos.write(mq.hashSum().getBytes());
			}
			if (fileContainer != null) {
				baos.write(fileContainer.hashSum().getBytes());
			}
			if (rtsp != null) {
				baos.write(rtsp.hashSum().getBytes());
			}
			baos.write(internalIsVirtual ? 1 : 0);
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamsSs that)) {
			return false;
		}
		return hashSum().equals(that.hashSum());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void postProcess() throws ConfigInvalidException {
		internalHasBeenPostProcessed = true;

		//
		//noinspection ConstantValue
		if (enabled == null) {
			enabled = true;
		}

		//
		if (rawFile != null) {
			rawFile.postProcess();
			internalEsSourceType = RtspProtoEsSourceType.ST_ES_RAW_FILE;
		} else if (mq != null) {
			mq.postProcess();
			internalEsSourceType = RtspProtoEsSourceType.ST_ES_MQ;
		} else if (fileContainer != null) {
			fileContainer.postProcess();
			internalEsSourceType = RtspProtoEsSourceType.ST_DEMUX_MS_FILE;
		} else if (rtsp != null) {
			rtsp.postProcess();
			internalEsSourceType = RtspProtoEsSourceType.ST_DEMUX_MS_RTSP;
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(@NonNull String extIdStr, @NonNull Path dataDirPath) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		int tmpSourcesCnt = 0;
		if (rawFile != null) { tmpSourcesCnt++; }
		if (mq != null) { tmpSourcesCnt++; }
		if (fileContainer != null) { tmpSourcesCnt++; }
		if (rtsp != null) { tmpSourcesCnt++; }
		if (tmpSourcesCnt == 0) {
			throw new ConfigInvalidException(FNC_NAME + ": No Sub-Stream Source defined " +
					"in Sub-Stream ID '" + extIdStr + "'");
		}
		if (tmpSourcesCnt > 1) {
			throw new ConfigInvalidException(FNC_NAME + ": More than one Sub-Stream Source defined " +
					"in Sub-Stream ID '" + extIdStr + "'");
		}
		if (! enabled) {
			return;
		}
		switch (internalEsSourceType) {
			case ST_ES_RAW_FILE -> Objects.requireNonNull(rawFile).validate(extIdStr, dataDirPath);
			case ST_ES_MQ -> Objects.requireNonNull(mq).validate(extIdStr);
			case ST_DEMUX_MS_FILE -> {
				if (! internalIsVirtual) {
					Objects.requireNonNull(fileContainer).validate(extIdStr, dataDirPath);
				}
			}
			case ST_DEMUX_MS_RTSP -> {
				if (! internalIsVirtual) {
					Objects.requireNonNull(rtsp).validate(extIdStr);
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}
