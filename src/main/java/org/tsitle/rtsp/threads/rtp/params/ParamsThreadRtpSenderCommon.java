package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.security.SrtxpContext;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ParamsThreadRtpSenderCommon implements Cloneable {

	public record RtpTsT0(int rtpTsT0, long rtpGenTsT0Ns) implements Cloneable {
		@Override
		public RtpTsT0 clone() {
			try {
				return (RtpTsT0)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	/** Logging interface */
	private LogMsgInterface logMsgInterface;
	private boolean isSetLogMsgInterface;

	/** Debugging: Session ID */
	private String debugSessionId;
	private boolean isSetDebugSessionId;
	/** Debugging: Rewind media files? */
	private boolean debugRewindMediaFiles;
	private boolean isSetDebugRewindMediaFiles;

	/** Stream Source ID (not the SSRC) */
	private int streamSourceId;
	private boolean isSetStreamSourceId;
	/** Is Stream Source read from a file? */
	private boolean isStreamSourceFromFile;
	private boolean isSetIsStreamSourceFromFile;

	/** Client IP address */
	private InetAddress clientIpAddr;
	private boolean isSetClientIpAddr;
	/** Destination port for RTP packets (audio and video), provided by the RTSP Client */
	private int clientDestPortRtp;
	private boolean isSetClientDestPortRtp;

	/** UDP socket for outgoing RTP packets */
	private DatagramSocket rtpSocketUdp;
	private boolean isSetRtpSocketUdp;

	/** Video or audio frames per second */
	private double avFramesPerSecond;
	private boolean isSetAvFramesPerSecond;

	/** Sequence number for RTP packets (16 bits unsigned) */
	private short rtpSeqNrT0;
	private boolean isSetRtpSeqNrT0;
	/** Initial RTP Timestamp within the session */
	private RtpTsT0 rtpTimestampT0;
	private boolean isSetRtpTimestampT0;
	/** RTSP Synchronization Source Identifier of the stream */
	private int rtspSsrcId;
	private boolean isSetRtspSsrcId;

	/** XSRC block for SDES RTCP packets (for communicating which streams belong to the same session) */
	private RtcpInnerXsrcBlock xsrcBlockEntry;
	private boolean isSetXsrcBlockEntry;
	/** Callback for appending RTCP packets to the outgoing queue */
	private BiConsumer<Integer, BufferExt> cbRtcpAppendToOutgoingQueque;
	private boolean isSetCbRtcpAppendToOutgoingQueque;

	/** Callback for notifying the parent thread that the child thread is ready to start */
	private Consumer<Integer> cbNotifyThreadReady;
	private boolean isSetCbNotifyThreadReady;
	/** Callback for checking if the child thread may start playback */
	private Supplier<Boolean> cbThreadMayStartPlayback;
	private boolean isSetCbThreadMayStartPlayback;

	/** Incoming A/V stream URI */
	private URI avStreamIncomingUri;
	private boolean isSetAvStreamIncomingUri;

	/** Is RTP/RTCP encryption enabled? */
	private boolean isRtxpEncryptionEnabled;
	private boolean isSetIsRtxpEncryptionEnabled;
	/** SRTxP context */
	private SrtxpContext srtxpContext;
	private boolean isSetSrtxpContext;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<LogMsgInterface> getLogMsgInterface() { return Optional.ofNullable(logMsgInterface); }
	public void setLogMsgInterface(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
		this.isSetLogMsgInterface = true;
	}

	@SuppressWarnings("unused")
	public Optional<String> getDebugSessionId() { return Optional.ofNullable(debugSessionId); }
	public void setDebugSessionId(@NonNull String debugSessionId) {
		this.debugSessionId = debugSessionId;
		this.isSetDebugSessionId = true;
	}

	public boolean getDebugRewindMediaFiles() { return debugRewindMediaFiles; }
	public void setDebugRewindMediaFiles(boolean debugRewindMediaFiles) {
		this.debugRewindMediaFiles = debugRewindMediaFiles;
		this.isSetDebugRewindMediaFiles = true;
	}

	public int getStreamSourceId() { return streamSourceId; }
	public void setStreamSourceId(int streamSourceId) {
		this.streamSourceId = streamSourceId;
		this.isSetStreamSourceId = true;
	}

	public boolean getIsStreamSourceFromFile() { return isStreamSourceFromFile; }
	public void setIsStreamSourceFromFile(boolean value) {
		this.isStreamSourceFromFile = value;
		this.isSetIsStreamSourceFromFile = true;
	}

	public Optional<InetAddress> getClientIpAddr() { return Optional.ofNullable(clientIpAddr); }
	public void setClientIpAddr(@NonNull InetAddress clientIpAddr) {
		this.clientIpAddr = clientIpAddr;
		this.isSetClientIpAddr = true;
	}

	public int getClientDestPortRtp() { return clientDestPortRtp; }
	public void setClientDestPortRtp(int clientDestPortRtp) {
		this.clientDestPortRtp = clientDestPortRtp;
		this.isSetClientDestPortRtp = true;
	}

	public Optional<DatagramSocket> getRtpSocketUdp() { return Optional.ofNullable(rtpSocketUdp); }
	public void setRtpSocketUdp(@NonNull DatagramSocket rtpSocketUdp) {
		this.rtpSocketUdp = rtpSocketUdp;
		this.isSetRtpSocketUdp = true;
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

	public int getRtspSsrcId() { return rtspSsrcId; }
	public void setRtspSsrcId(int rtspSsrcId) {
		this.rtspSsrcId = rtspSsrcId;
		this.isSetRtspSsrcId = true;
	}

	public Optional<RtcpInnerXsrcBlock> getXsrcBlockEntry() { return Optional.ofNullable(xsrcBlockEntry.clone()); }
	public void setXsrcBlockEntry(@NonNull RtcpInnerXsrcBlock xsrcBlockEntry) {
		this.xsrcBlockEntry = xsrcBlockEntry.clone();
		this.isSetXsrcBlockEntry = true;
	}

	public Optional<BiConsumer<@NonNull Integer, @NonNull BufferExt>> getCbRtcpAppendToOutgoingQueque() { return Optional.ofNullable(cbRtcpAppendToOutgoingQueque); }
	public void setCbRtcpAppendToOutgoingQueque(@NonNull BiConsumer<@NonNull Integer, @NonNull BufferExt> cbRtcpAppendToOutgoingQueque) {
		this.cbRtcpAppendToOutgoingQueque = cbRtcpAppendToOutgoingQueque;
		this.isSetCbRtcpAppendToOutgoingQueque = true;
	}

	public Optional<Consumer<Integer>> getCbNotifyThreadReady() { return Optional.ofNullable(cbNotifyThreadReady); }
	public void setCbNotifyThreadReady(@NonNull Consumer<@NonNull Integer> cbNotifyThreadReady) {
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

	public boolean getIsRtxpEncryptionEnabled() { return isRtxpEncryptionEnabled; }
	public void setIsRtxpEncryptionEnabled(boolean value) {
		this.isRtxpEncryptionEnabled = value;
		this.isSetIsRtxpEncryptionEnabled = true;
	}

	public Optional<SrtxpContext> getSrtxpContext() { return Optional.ofNullable(srtxpContext); }
	public void setSrtxpContext(@NonNull SrtxpContext value) {
		this.srtxpContext = value.clone();
		this.isSetSrtxpContext = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public ParamsThreadRtpSenderCommon clone() {
		try {
			ParamsThreadRtpSenderCommon clone = (ParamsThreadRtpSenderCommon)super.clone();
			//
			if (rtpTimestampT0 != null) {
				clone.rtpTimestampT0 = rtpTimestampT0.clone();
			}
			//
			if (xsrcBlockEntry != null) {
				clone.xsrcBlockEntry = xsrcBlockEntry.clone();
			}
			//
			try {
				if (clientIpAddr != null) {
					clone.clientIpAddr = InetAddress.getByAddress(clientIpAddr.getAddress());
				}
			} catch (UnknownHostException e) {
				// this should never happen
				throw new RuntimeException(e);
			}
			//
			if (srtxpContext != null) {
				clone.srtxpContext = srtxpContext.clone();
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetLogMsgInterface, "logMsgInterface");

		requireIsSet(isSetDebugSessionId, "debugSessionId");
		requireIsSet(isSetDebugRewindMediaFiles, "debugRewindMediaFiles");

		requireIsSet(isSetStreamSourceId, "isSetStreamSourceId");
		requireIsSet(isSetIsStreamSourceFromFile, "isSetIsStreamSourceFromFile");

		requireIsSet(isSetClientIpAddr, "clientIpAddr");
		requireIsSet(isSetClientDestPortRtp, "clientDestPortRtp");

		requireIsSet(isSetRtpSocketUdp, "rtpSocketUdp");

		requireIsSet(isSetAvFramesPerSecond, "avFramesPerSecond");

		requireIsSet(isSetRtpSeqNrT0, "rtpSeqNrT0");
		requireIsSet(isSetRtpTimestampT0, "rtpTimestampT0");
		requireIsSet(isSetRtspSsrcId, "rtspSsrcId");

		requireIsSet(isSetXsrcBlockEntry, "xsrcBlockEntries");
		requireIsSet(isSetCbRtcpAppendToOutgoingQueque, "cbRtcpAppendToOutgoingQueque");

		requireIsSet(isSetCbNotifyThreadReady, "cbNotifyThreadReady");
		requireIsSet(isSetCbThreadMayStartPlayback, "cbThreadMayStartPlayback");

		requireIsSet(isSetAvStreamIncomingUri, "avStreamIncomingUri");

		requireIsSet(isSetIsRtxpEncryptionEnabled, "isRtxpEncryptionEnabled");
		requireIsSet(isSetSrtxpContext, "srtxpContext");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		requireNonNull(logMsgInterface, "logMsgInterface");

		requireNonNull(debugSessionId, "debugSessionId");
		if (debugSessionId.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "debugSessionId must not be empty");
		}

		if (streamSourceId < 0) {
			throw new IllegalArgumentException(errPrefix + "streamSourceId must be >= 0");
		}

		requireNonNull(clientIpAddr, "clientIpAddr");
		if (clientDestPortRtp <= 0 || clientDestPortRtp > 65535) {
			throw new IllegalArgumentException(errPrefix + "clientDestPortRtp must be > 0 and <= 65535");
		}

		requireNonNull(rtpSocketUdp, "rtpSocketUdp");
		if (avFramesPerSecond <= 0.1f || avFramesPerSecond > 100.0f) {
			throw new IllegalArgumentException(errPrefix + "avFramesPerSecond must be > 0.1 and <= 100.0");
		}

		requireNonNull(rtpTimestampT0, "rtpTimestampT0");

		requireNonNull(xsrcBlockEntry, "xsrcBlockEntry");
		requireNonNull(cbRtcpAppendToOutgoingQueque, "cbRtcpAppendToOutgoingQueque");

		requireNonNull(cbNotifyThreadReady, "cbNotifyThreadReady");
		requireNonNull(cbThreadMayStartPlayback, "cbThreadMayStartPlayback");

		requireNonNull(avStreamIncomingUri, "avStreamIncomingUri");

		requireNonNull(srtxpContext, "srtxpContext");
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
