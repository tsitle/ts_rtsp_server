package org.tsitle.lib_dataprov.threadparams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.threads_dmxFc.TdpDemuxFcReadNextAvPacketInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.util.Optional;

public final class ParamsThreadDpCommon implements Cloneable {

	/** Logging interface */
	private @Nullable LogMsgInterface logMsgInterface = null;
	private boolean isSetLogMsgInterface;

	/** Are the parameters for a video thread? (if false, they are for an audio thread) */
	private boolean isVideoThread;
	private boolean isSetIsVideoThread;

	/** Elementary-Stream Source ID - not the SSRC */
	private @NonNull RtspProtoIdEsSource idEsSource = RtspProtoIdEsSource.ofEmpty();
	private boolean isSetIdEsSource;

	/** Incoming A/V stream URI */
	private @Nullable ProUri avStreamIncomingUri = null;
	private boolean isSetAvStreamIncomingUri;

	/** Optional: 'Demuxer: Read next A/V packet' instance */
	private @Nullable TdpDemuxFcReadNextAvPacketInterface demuxReadNextAvPacketInterface = null;

	public ParamsThreadDpCommon() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<LogMsgInterface> getLogMsgInterface() { return Optional.ofNullable(logMsgInterface); }
	public void setLogMsgInterface(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
		this.isSetLogMsgInterface = true;
	}

	public boolean getIsVideoThread() { return isVideoThread; }
	public void setIsVideoThread(boolean value) {
		this.isVideoThread = value;
		this.isSetIsVideoThread = true;
	}

	public @NonNull RtspProtoIdEsSource getIdEsSource() { return idEsSource; }
	public void setIdEsSource(@NonNull RtspProtoIdEsSource value) {
		this.idEsSource.copyFrom(value);
		this.idEsSource.writeProtect();
		this.isSetIdEsSource = true;
	}

	public Optional<ProUri> getAvStreamIncomingUri() { return Optional.ofNullable(avStreamIncomingUri); }
	public void setAvStreamIncomingUri(@NonNull ProUri avStreamIncomingUri) {
		this.avStreamIncomingUri = avStreamIncomingUri.clone();
		this.isSetAvStreamIncomingUri = true;
	}

	public Optional<TdpDemuxFcReadNextAvPacketInterface> getDemuxReadNextAvPacketInterface() { return Optional.ofNullable(demuxReadNextAvPacketInterface); }
	public void setDemuxReadNextAvPacketInterface(@NonNull TdpDemuxFcReadNextAvPacketInterface value) {
		this.demuxReadNextAvPacketInterface = value;
		// only required when actually demuxing
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadDpCommon clone() {
		try {
			ParamsThreadDpCommon clone = (ParamsThreadDpCommon)super.clone();
			clone.idEsSource = idEsSource.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetLogMsgInterface, "logMsgInterface");
		requireIsSet(isSetIsVideoThread, "isVideoThread");
		requireIsSet(isSetIdEsSource, "idEsSource");
		requireIsSet(isSetAvStreamIncomingUri, "avStreamIncomingUri");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		requireNonNull(logMsgInterface, "logMsgInterface");

		if (idEsSource.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "idEsSource must not be empty");
		}

		requireNonNull(avStreamIncomingUri, "avStreamIncomingUri");
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadDpCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadDpCommon.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
