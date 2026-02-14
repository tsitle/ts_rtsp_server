package org.tsitle.rtsp.threads.rtp.params;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ParamsThreadRtpSenderCommon implements Cloneable {

	/** Debugging: Session ID */
	private String debugSessionId;
	private boolean isSetDebugSessionId;
	/** Debugging: Rewind media files? */
	private boolean debugRewindMediaFiles;
	private boolean isSetDebugRewindMediaFiles;

	/** Stream Source ID (not the SSRC) */
	private int streamSourceId;
	private boolean isSetStreamSourceId;

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
	private float avFramesPerSecond;
	private boolean isSetAvFramesPerSecond;

	/** Sequence number for RTP packets (16 bits unsigned) */
	private short rtpSeqNrT0;
	private boolean isSetRtpSeqNrT0;
	/** Initial RTP Timestamp within the session */
	private int rtpTimestampT0;
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

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getDebugSessionId() { return Optional.ofNullable(debugSessionId); }
	public void setDebugSessionId(String debugSessionId) {
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

	public Optional<InetAddress> getClientIpAddr() { return Optional.ofNullable(clientIpAddr); }
	public void setClientIpAddr(InetAddress clientIpAddr) {
		this.clientIpAddr = clientIpAddr;
		this.isSetClientIpAddr = true;
	}

	public int getClientDestPortRtp() { return clientDestPortRtp; }
	public void setClientDestPortRtp(int clientDestPortRtp) {
		this.clientDestPortRtp = clientDestPortRtp;
		this.isSetClientDestPortRtp = true;
	}

	public Optional<DatagramSocket> getRtpSocketUdp() { return Optional.ofNullable(rtpSocketUdp); }
	public void setRtpSocketUdp(DatagramSocket rtpSocketUdp) {
		this.rtpSocketUdp = rtpSocketUdp;
		this.isSetRtpSocketUdp = true;
	}

	public float getAvFramesPerSecond() { return avFramesPerSecond; }
	public void setAvFramesPerSecond(float avFramesPerSecond) {
		this.avFramesPerSecond = avFramesPerSecond;
		this.isSetAvFramesPerSecond = true;
	}

	public short getRtpSeqNrT0() { return rtpSeqNrT0; }
	public void setRtpSeqNrT0(short rtpSeqNrT0) {
		this.rtpSeqNrT0 = rtpSeqNrT0;
		this.isSetRtpSeqNrT0 = true;
	}

	public int getRtpTimestampT0() { return rtpTimestampT0; }
	public void setRtpTimestampT0(int rtpTimestampT0) {
		this.rtpTimestampT0 = rtpTimestampT0;
		this.isSetRtpTimestampT0 = true;
	}

	public int getRtspSsrcId() { return rtspSsrcId; }
	public void setRtspSsrcId(int rtspSsrcId) {
		this.rtspSsrcId = rtspSsrcId;
		this.isSetRtspSsrcId = true;
	}

	public Optional<RtcpInnerXsrcBlock> getXsrcBlockEntry() { return Optional.ofNullable(xsrcBlockEntry.clone()); }
	public void setXsrcBlockEntry(RtcpInnerXsrcBlock xsrcBlockEntry) {
		this.xsrcBlockEntry = xsrcBlockEntry.clone();
		this.isSetXsrcBlockEntry = true;
	}

	public Optional<BiConsumer<Integer, BufferExt>> getCbRtcpAppendToOutgoingQueque() { return Optional.ofNullable(cbRtcpAppendToOutgoingQueque); }
	public void setCbRtcpAppendToOutgoingQueque(BiConsumer<Integer, BufferExt> cbRtcpAppendToOutgoingQueque) {
		this.cbRtcpAppendToOutgoingQueque = cbRtcpAppendToOutgoingQueque;
		this.isSetCbRtcpAppendToOutgoingQueque = true;
	}

	public Optional<Consumer<Integer>> getCbNotifyThreadReady() { return Optional.ofNullable(cbNotifyThreadReady); }
	public void setCbNotifyThreadReady(Consumer<Integer> cbNotifyThreadReady) {
		this.cbNotifyThreadReady = cbNotifyThreadReady;
		this.isSetCbNotifyThreadReady = true;
	}

	public Optional<Supplier<Boolean>> getCbThreadMayStartPlayback() { return Optional.ofNullable(cbThreadMayStartPlayback); }
	public void setCbThreadMayStartPlayback(Supplier<Boolean> cbThreadMayStartPlayback) {
		this.cbThreadMayStartPlayback = cbThreadMayStartPlayback;
		this.isSetCbThreadMayStartPlayback = true;
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
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetDebugSessionId, "debugSessionId");
		requireIsSet(isSetDebugRewindMediaFiles, "debugRewindMediaFiles");

		requireIsSet(isSetStreamSourceId, "isSetStreamSourceId");

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
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

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

		requireNonNull(xsrcBlockEntry, "xsrcBlockEntry");
		requireNonNull(cbRtcpAppendToOutgoingQueque, "cbRtcpAppendToOutgoingQueque");

		requireNonNull(cbNotifyThreadReady, "cbNotifyThreadReady");
		requireNonNull(cbThreadMayStartPlayback, "cbThreadMayStartPlayback");
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
