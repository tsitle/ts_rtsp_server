package org.tsitle.rtsp_server.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;
import org.tsitle.lib_dataprov.threads_dmxFc.TdpDemuxFcReadNextAvPacketInterface;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ParamsThreadRtpSenderCommon extends ParamsThreadRtxp implements Cloneable {

	public static final class RtpTsT0WithMonoRef implements Cloneable {
		private @NonNull RtspProtoRtpTimestamp rtpTsT0;
		private @NonNull TimestampMonotonic rtpGenTsT0Ns;

		public RtpTsT0WithMonoRef(@NonNull RtspProtoRtpTimestamp rtpTsT0, @NonNull TimestampMonotonic rtpGenTsT0Ns) {
			this.rtpTsT0 = rtpTsT0.clone();
			this.rtpGenTsT0Ns = rtpGenTsT0Ns.clone();
		}

		public boolean isEmpty() {
			return (rtpTsT0.isEmpty() || rtpGenTsT0Ns.isEmpty());
		}

		@Override
		public @NonNull RtpTsT0WithMonoRef clone() {
			try {
				RtpTsT0WithMonoRef cloned = (RtpTsT0WithMonoRef) super.clone();
				cloned.rtpTsT0 = rtpTsT0.clone();
				cloned.rtpGenTsT0Ns = rtpGenTsT0Ns.clone();
				return cloned;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}

		public @NonNull RtspProtoRtpTimestamp rtpTsT0() { return rtpTsT0.clone(); }

		@SuppressWarnings("unused")
		public @NonNull TimestampMonotonic rtpGenTsT0Ns() { return rtpGenTsT0Ns.clone(); }

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null || obj.getClass() != this.getClass()) {
				return false;
			}
			var that = (RtpTsT0WithMonoRef)obj;
			return Objects.equals(this.rtpTsT0, that.rtpTsT0) && Objects.equals(this.rtpGenTsT0Ns, that.rtpGenTsT0Ns);
		}

		@Override
		public int hashCode() {
			return Objects.hash(rtpTsT0, rtpGenTsT0Ns);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Are the parameters for a video thread? (if false, they are for an audio thread) */
	private boolean isVideoThread;
	private boolean isSetIsVideoThread;

	/** Elementary-Stream Source Type */
	private @Nullable RtspProtoEsSourceType esSourceType = null;
	private boolean isSetEsSourceType;

	/** Video or audio frames per second */
	private double avFramesPerSecond;
	private boolean isSetAvFramesPerSecond;

	/** Sequence number for RTP packets (16 bits unsigned) */
	private @Nullable RtspProtoRtpSeqNr rtpSeqNrT0 = null;
	private boolean isSetRtpSeqNrT0;
	/** Initial RTP Timestamp within the session */
	private @Nullable RtpTsT0WithMonoRef rtpTimestampT0WithMonoRef = null;
	private boolean isSetRtpTimestampT0WithMonoRef;

	/** XSRC block for SDES RTCP packets (for communicating which streams belong to the same session) */
	private @Nullable RtcpInnerXsrcBlock xsrcBlockEntry = null;
	private boolean isSetXsrcBlockEntry;
	/** Callback for appending RTCP SR (Sender Report) compound packets to the outgoing queue */
	private @Nullable BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt> cbRtcpAppendSrToOutgoingQueue = null;
	private boolean isSetCbRtcpAppendSrToOutgoingQueue;
	/** Callback for appending RTCP BYE packets to the outgoing queue */
	private @Nullable Consumer<@NonNull RtspProtoIdXsrc> cbRtcpAppendByeToOutgoingQueue = null;
	private boolean isSetCbRtcpAppendByeToOutgoingQueue;

	/** Callback for notifying the parent thread that the child thread is ready to start */
	private @Nullable Consumer<@NonNull RtspProtoIdSubStream> cbNotifyThreadReady = null;
	private boolean isSetCbNotifyThreadReady;
	/** Callback for checking if the child thread may start playback */
	private @Nullable Supplier<@NonNull Boolean> cbThreadMayStartPlayback = null;
	private boolean isSetCbThreadMayStartPlayback;

	/** Incoming A/V stream URI */
	private @Nullable ProUri avStreamIncomingUri = null;
	private boolean isSetAvStreamIncomingUri;

	/** Optional: 'Demuxer: Read next A/V packet' instance */
	private @Nullable TdpDemuxFcReadNextAvPacketInterface dmxFcReadNextAvPacketInterface = null;

	public ParamsThreadRtpSenderCommon() {
		super(false, true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean getIsVideoThread() { return isVideoThread; }
	public void setIsVideoThread(boolean value) {
		this.isVideoThread = value;
		this.isSetIsVideoThread = true;
	}

	public Optional<RtspProtoEsSourceType> getEsSourceType() { return Optional.ofNullable(esSourceType); }
	public void setEsSourceType(@NonNull RtspProtoEsSourceType value) {
		this.esSourceType = value;
		this.isSetEsSourceType = true;
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

	public Optional<RtpTsT0WithMonoRef> getRtpTimestampT0WithMonoRef() { return Optional.ofNullable(rtpTimestampT0WithMonoRef); }
	public void setRtpTimestampT0WithMonoRef(@NonNull RtpTsT0WithMonoRef value) {
		this.rtpTimestampT0WithMonoRef = value.clone();
		this.isSetRtpTimestampT0WithMonoRef = true;
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

	public Optional<BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt>> getCbRtcpAppendSrToOutgoingQueue() {
		return Optional.ofNullable(cbRtcpAppendSrToOutgoingQueue);
	}
	public void setCbRtcpAppendSrToOutgoingQueue(
				@NonNull BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt> cbRtcpAppendSrToOutgoingQueue
			) {
		this.cbRtcpAppendSrToOutgoingQueue = cbRtcpAppendSrToOutgoingQueue;
		this.isSetCbRtcpAppendSrToOutgoingQueue = true;
	}

	public Optional<Consumer<@NonNull RtspProtoIdXsrc>> getCbRtcpAppendByeToOutgoingQueue() {
		return Optional.ofNullable(cbRtcpAppendByeToOutgoingQueue);
	}
	public void setCbRtcpAppendByeToOutgoingQueue(
				@NonNull Consumer<@NonNull RtspProtoIdXsrc> cbRtcpAppendByeToOutgoingQueue
			) {
		this.cbRtcpAppendByeToOutgoingQueue = cbRtcpAppendByeToOutgoingQueue;
		this.isSetCbRtcpAppendByeToOutgoingQueue = true;
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

	public Optional<ProUri> getAvStreamIncomingUri() { return Optional.ofNullable(avStreamIncomingUri); }
	public void setAvStreamIncomingUri(@NonNull ProUri avStreamIncomingUri) {
		this.avStreamIncomingUri = avStreamIncomingUri.clone();
		this.isSetAvStreamIncomingUri = true;
	}

	public Optional<TdpDemuxFcReadNextAvPacketInterface> getDmxFcReadNextAvPacketInterface() { return Optional.ofNullable(dmxFcReadNextAvPacketInterface); }
	public void setDmxFcReadNextAvPacketInterface(@NonNull TdpDemuxFcReadNextAvPacketInterface value) {
		this.dmxFcReadNextAvPacketInterface = value;
		// only required when actually demuxing
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
		if (rtpTimestampT0WithMonoRef != null) {
			clone.rtpTimestampT0WithMonoRef = rtpTimestampT0WithMonoRef.clone();
		}
		//
		if (xsrcBlockEntry != null) {
			clone.xsrcBlockEntry = xsrcBlockEntry.clone();
		}
		return clone;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull ParamsThreadDpCommon copyToThreadDpCommon() {
		ParamsThreadDpCommon resObj = new ParamsThreadDpCommon();
		resObj.setLogMsgInterface(getLogMsgInterface().orElseThrow());
		resObj.setIsVideoThread(getIsVideoThread());
		resObj.setIdEsSource(getIdEsSource());
		resObj.setAvStreamIncomingUri(getAvStreamIncomingUri().orElseThrow());
		if (getDmxFcReadNextAvPacketInterface().isPresent()) {
			resObj.setDemuxReadNextAvPacketInterface(getDmxFcReadNextAvPacketInterface().orElseThrow());
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetIsVideoThread, "isVideoThread");

		requireIsSet(isSetEsSourceType, "esSourceType");

		requireIsSet(isSetAvFramesPerSecond, "avFramesPerSecond");

		requireIsSet(isSetRtpSeqNrT0, "rtpSeqNrT0");
		requireIsSet(isSetRtpTimestampT0WithMonoRef, "rtpTimestampT0WithMonoRef");

		requireIsSet(isSetXsrcBlockEntry, "xsrcBlockEntries");
		requireIsSet(isSetCbRtcpAppendSrToOutgoingQueue, "cbRtcpAppendSrToOutgoingQueue");
		requireIsSet(isSetCbRtcpAppendByeToOutgoingQueue, "cbRtcpAppendByeToOutgoingQueue");

		requireIsSet(isSetCbNotifyThreadReady, "cbNotifyThreadReady");
		requireIsSet(isSetCbThreadMayStartPlayback, "cbThreadMayStartPlayback");

		requireIsSet(isSetAvStreamIncomingUri, "avStreamIncomingUri");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (avFramesPerSecond > 100.0f) {
			throw new IllegalArgumentException(errPrefix + "avFramesPerSecond must be <= 100.0 (is=" +
					String.format("%.3f", avFramesPerSecond) + ")");
		}

		requireNonNull(rtpSeqNrT0, "rtpSeqNrT0");
		if (rtpSeqNrT0.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "rtpSeqNrT0 must not be empty");
		}
		requireNonNull(rtpTimestampT0WithMonoRef, "rtpTimestampT0WithMonoRef");
		if (rtpTimestampT0WithMonoRef.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "rtpTimestampT0WithMonoRef must not be empty");
		}

		requireNonNull(xsrcBlockEntry, "xsrcBlockEntry");
		requireNonNull(cbRtcpAppendSrToOutgoingQueue, "cbRtcpAppendSrToOutgoingQueue");
		requireNonNull(cbRtcpAppendByeToOutgoingQueue, "cbRtcpAppendByeToOutgoingQueue");

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
