package org.tsitle.rtsp_server.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ParamsThreadRtpSenderCommon extends ParamsThreadRtxp implements Cloneable {

	public static final class RtpTsT0WithEpoch implements Cloneable {
		private @NonNull RtspProtoRtpTimestamp rtpTsT0;
		private @NonNull TimestampEpochNs rtpGenTsT0Ns;

		public RtpTsT0WithEpoch(@NonNull RtspProtoRtpTimestamp rtpTsT0, @NonNull TimestampEpochNs rtpGenTsT0Ns) {
			this.rtpTsT0 = rtpTsT0.clone();
			this.rtpGenTsT0Ns = rtpGenTsT0Ns.clone();
		}

		public boolean isEmpty() {
			return (rtpTsT0.isEmpty() || rtpGenTsT0Ns.isEmpty());
		}

		@Override
		public @NonNull RtpTsT0WithEpoch clone() {
			try {
				RtpTsT0WithEpoch cloned = (RtpTsT0WithEpoch) super.clone();
				cloned.rtpTsT0 = rtpTsT0.clone();
				cloned.rtpGenTsT0Ns = rtpGenTsT0Ns.clone();
				return cloned;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}

		public @NonNull RtspProtoRtpTimestamp rtpTsT0() { return rtpTsT0.clone(); }

		public @NonNull TimestampEpochNs rtpGenTsT0Ns() { return rtpGenTsT0Ns.clone(); }

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null || obj.getClass() != this.getClass()) {
				return false;
			}
			var that = (RtpTsT0WithEpoch)obj;
			return Objects.equals(this.rtpTsT0, that.rtpTsT0) && Objects.equals(this.rtpGenTsT0Ns, that.rtpGenTsT0Ns);
		}

		@Override
		public int hashCode() {
			return Objects.hash(rtpTsT0, rtpGenTsT0Ns);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Debugging: Rewind media files? */
	private boolean debugRewindMediaFiles;
	private boolean isSetDebugRewindMediaFiles;

	/** Is Stream Source read from a file? */
	private boolean isStreamSourceFromFile;
	private boolean isSetIsStreamSourceFromFile;

	/** Video or audio frames per second */
	private double avFramesPerSecond;
	private boolean isSetAvFramesPerSecond;

	/** Sequence number for RTP packets (16 bits unsigned) */
	private @Nullable RtspProtoRtpSeqNr rtpSeqNrT0 = null;
	private boolean isSetRtpSeqNrT0;
	/** Initial RTP Timestamp within the session */
	private @Nullable RtpTsT0WithEpoch rtpTimestampT0WithEpoch = null;
	private boolean isSetRtpTimestampT0WithEpoch;

	/** XSRC block for SDES RTCP packets (for communicating which streams belong to the same session) */
	private @Nullable RtcpInnerXsrcBlock xsrcBlockEntry = null;
	private boolean isSetXsrcBlockEntry;
	/** Callback for appending RTCP packets to the outgoing queue */
	private @Nullable BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt> cbRtcpAppendToOutgoingQueue = null;
	private boolean isSetCbRtcpAppendToOutgoingQueue;

	/** Callback for notifying the parent thread that the child thread is ready to start */
	private @Nullable Consumer<@NonNull RtspProtoIdSubStream> cbNotifyThreadReady = null;
	private boolean isSetCbNotifyThreadReady;
	/** Callback for checking if the child thread may start playback */
	private @Nullable Supplier<@NonNull Boolean> cbThreadMayStartPlayback = null;
	private boolean isSetCbThreadMayStartPlayback;

	/** Incoming A/V stream URI */
	private @Nullable URI avStreamIncomingUri = null;
	private boolean isSetAvStreamIncomingUri;

	public ParamsThreadRtpSenderCommon() {
		super(false, true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean getDebugRewindMediaFiles() { return debugRewindMediaFiles; }
	public void setDebugRewindMediaFiles(boolean debugRewindMediaFiles) {
		this.debugRewindMediaFiles = debugRewindMediaFiles;
		this.isSetDebugRewindMediaFiles = true;
	}

	public boolean getIsStreamSourceFromFile() { return isStreamSourceFromFile; }
	public void setIsStreamSourceFromFile(boolean value) {
		this.isStreamSourceFromFile = value;
		this.isSetIsStreamSourceFromFile = true;
	}

	public double getAvFramesPerSecond() { return avFramesPerSecond; }
	public void setAvFramesPerSecond(double avFramesPerSecond) {
		this.avFramesPerSecond = avFramesPerSecond;
		this.isSetAvFramesPerSecond = true;
	}

	public Optional<RtspProtoRtpSeqNr> getRtpSeqNrT0() { return Optional.ofNullable(rtpSeqNrT0); }
	public void setRtpSeqNrT0(@NonNull RtspProtoRtpSeqNr rtpSeqNrT0) {
		this.rtpSeqNrT0 = rtpSeqNrT0.clone();
		this.isSetRtpSeqNrT0 = true;
	}

	public Optional<RtpTsT0WithEpoch> getRtpTimestampT0WithEpoch() { return Optional.ofNullable(rtpTimestampT0WithEpoch); }
	public void setRtpTimestampT0WithEpoch(@NonNull RtpTsT0WithEpoch value) {
		this.rtpTimestampT0WithEpoch = value.clone();
		this.isSetRtpTimestampT0WithEpoch = true;
	}

	public Optional<RtcpInnerXsrcBlock> getXsrcBlockEntry() {
		if (xsrcBlockEntry == null) {
			return Optional.empty();
		}
		return Optional.of(xsrcBlockEntry.clone());
	}
	public void setXsrcBlockEntry(@NonNull RtcpInnerXsrcBlock xsrcBlockEntry) {
		this.xsrcBlockEntry = xsrcBlockEntry.clone();
		this.isSetXsrcBlockEntry = true;
	}

	public Optional<BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt>> getCbRtcpAppendToOutgoingQueue() {
		return Optional.ofNullable(cbRtcpAppendToOutgoingQueue);
	}
	public void setCbRtcpAppendToOutgoingQueue(@NonNull BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt> cbRtcpAppendToOutgoingQueue) {
		this.cbRtcpAppendToOutgoingQueue = cbRtcpAppendToOutgoingQueue;
		this.isSetCbRtcpAppendToOutgoingQueue = true;
	}

	public Optional<Consumer<@NonNull RtspProtoIdSubStream>> getCbNotifyThreadReady() { return Optional.ofNullable(cbNotifyThreadReady); }
	public void setCbNotifyThreadReady(@NonNull Consumer<@NonNull RtspProtoIdSubStream> cbNotifyThreadReady) {
		this.cbNotifyThreadReady = cbNotifyThreadReady;
		this.isSetCbNotifyThreadReady = true;
	}

	public Optional<Supplier<@NonNull Boolean>> getCbThreadMayStartPlayback() { return Optional.ofNullable(cbThreadMayStartPlayback); }
	public void setCbThreadMayStartPlayback(@NonNull Supplier<@NonNull Boolean> cbThreadMayStartPlayback) {
		this.cbThreadMayStartPlayback = cbThreadMayStartPlayback;
		this.isSetCbThreadMayStartPlayback = true;
	}

	public Optional<URI> getAvStreamIncomingUri() { return Optional.ofNullable(avStreamIncomingUri); }
	public void setAvStreamIncomingUri(@NonNull URI avStreamIncomingUri) {
		this.avStreamIncomingUri = URI.create(avStreamIncomingUri.toString());
		this.isSetAvStreamIncomingUri = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		super.validate();
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtpSenderCommon clone() {
		ParamsThreadRtpSenderCommon clone = (ParamsThreadRtpSenderCommon)super.clone();
		//
		if (rtpSeqNrT0 != null) {
			clone.rtpSeqNrT0 = rtpSeqNrT0.clone();
		}
		//
		if (rtpTimestampT0WithEpoch != null) {
			clone.rtpTimestampT0WithEpoch = rtpTimestampT0WithEpoch.clone();
		}
		//
		if (xsrcBlockEntry != null) {
			clone.xsrcBlockEntry = xsrcBlockEntry.clone();
		}
		return clone;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetDebugRewindMediaFiles, "debugRewindMediaFiles");

		requireIsSet(isSetIsStreamSourceFromFile, "isSetIsStreamSourceFromFile");

		requireIsSet(isSetAvFramesPerSecond, "avFramesPerSecond");

		requireIsSet(isSetRtpSeqNrT0, "rtpSeqNrT0");
		requireIsSet(isSetRtpTimestampT0WithEpoch, "rtpTimestampT0WithEpoch");

		requireIsSet(isSetXsrcBlockEntry, "xsrcBlockEntries");
		requireIsSet(isSetCbRtcpAppendToOutgoingQueue, "cbRtcpAppendToOutgoingQueue");

		requireIsSet(isSetCbNotifyThreadReady, "cbNotifyThreadReady");
		requireIsSet(isSetCbThreadMayStartPlayback, "cbThreadMayStartPlayback");

		requireIsSet(isSetAvStreamIncomingUri, "avStreamIncomingUri");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (avFramesPerSecond <= 0.1f || avFramesPerSecond > 100.0f) {
			throw new IllegalArgumentException(errPrefix + "avFramesPerSecond must be > 0.1 and <= 100.0");
		}

		requireNonNull(rtpSeqNrT0, "rtpSeqNrT0");
		if (rtpSeqNrT0.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "rtpSeqNrT0 must not be empty");
		}
		requireNonNull(rtpTimestampT0WithEpoch, "rtpTimestampT0WithEpoch");
		if (rtpTimestampT0WithEpoch.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "rtpTimestampT0WithEpoch must not be empty");
		}

		requireNonNull(xsrcBlockEntry, "xsrcBlockEntry");
		requireNonNull(cbRtcpAppendToOutgoingQueue, "cbRtcpAppendToOutgoingQueue");

		requireNonNull(cbNotifyThreadReady, "cbNotifyThreadReady");
		requireNonNull(cbThreadMayStartPlayback, "cbThreadMayStartPlayback");

		requireNonNull(avStreamIncomingUri, "avStreamIncomingUri");
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtpSenderCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtpSenderCommon.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
