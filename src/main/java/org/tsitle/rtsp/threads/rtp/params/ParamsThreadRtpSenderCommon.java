package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

import java.net.URI;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ParamsThreadRtpSenderCommon extends ParamsThreadRtxp implements Cloneable {

	public record RtpTsT0(int rtpTsT0, long rtpGenTsT0Ns) implements Cloneable {
		@Override
		public @NonNull RtpTsT0 clone() {
			try {
				return (RtpTsT0)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

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
	private short rtpSeqNrT0;
	private boolean isSetRtpSeqNrT0;
	/** Initial RTP Timestamp within the session */
	private RtpTsT0 rtpTimestampT0;
	private boolean isSetRtpTimestampT0;

	/** XSRC block for SDES RTCP packets (for communicating which streams belong to the same session) */
	private @Nullable RtcpInnerXsrcBlock xsrcBlockEntry = null;
	private boolean isSetXsrcBlockEntry;
	/** Callback for appending RTCP packets to the outgoing queue */
	private BiConsumer<RtspProtoIdXsrc, BufferExt> cbRtcpAppendToOutgoingQueue;
	private boolean isSetCbRtcpAppendToOutgoingQueue;

	/** Callback for notifying the parent thread that the child thread is ready to start */
	private Consumer<@NonNull RtspProtoIdStreamSource> cbNotifyThreadReady;
	private boolean isSetCbNotifyThreadReady;
	/** Callback for checking if the child thread may start playback */
	private Supplier<Boolean> cbThreadMayStartPlayback;
	private boolean isSetCbThreadMayStartPlayback;

	/** Incoming A/V stream URI */
	private URI avStreamIncomingUri;
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

	public short getRtpSeqNrT0() { return rtpSeqNrT0; }
	public void setRtpSeqNrT0(short rtpSeqNrT0) {
		this.rtpSeqNrT0 = rtpSeqNrT0;
		this.isSetRtpSeqNrT0 = true;
	}

	public Optional<RtpTsT0> getRtpTimestampT0() { return Optional.ofNullable(rtpTimestampT0); }
	public void setRtpTimestampT0(@NonNull RtpTsT0 value) {
		this.rtpTimestampT0 = value;
		this.isSetRtpTimestampT0 = true;
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

	public Optional<Consumer<RtspProtoIdStreamSource>> getCbNotifyThreadReady() { return Optional.ofNullable(cbNotifyThreadReady); }
	public void setCbNotifyThreadReady(@NonNull Consumer<@NonNull RtspProtoIdStreamSource> cbNotifyThreadReady) {
		this.cbNotifyThreadReady = cbNotifyThreadReady;
		this.isSetCbNotifyThreadReady = true;
	}

	public Optional<Supplier<Boolean>> getCbThreadMayStartPlayback() { return Optional.ofNullable(cbThreadMayStartPlayback); }
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
		if (rtpTimestampT0 != null) {
			clone.rtpTimestampT0 = rtpTimestampT0.clone();
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
		requireIsSet(isSetRtpTimestampT0, "rtpTimestampT0");

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

		requireNonNull(rtpTimestampT0, "rtpTimestampT0");

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
