package org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_v_mjpeg.VideoJpegInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.codec_v_mjpeg.FrameGrabberVideoMjpegFromEsMq;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsFromMqBase;

public final class ThreadDataProvEsMjpegFromMq extends ThreadDataProvEsFromMqBase<VideoJpegInfo> {

	private final @NonNull PacketPacMjpeg packetPac;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvEsMjpegFromMq(
				@NonNull ParamsThreadDpCommon paramsCommon
			) {
		super(paramsCommon, false, true, false);

		this.packetPac = new PacketPacMjpeg(
				paramsCommon.getLogMsgInterface().orElseThrow()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberVideoMjpegFromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull VideoJpegInfo parseAndConvertData(@NonNull BufferExt ioBuf) throws AvInvalidCodecDataException {
		return packetPac.parseAndConvertData(debugStreamOffset, ioBuf);
	}

	@Override
	protected @NonNull VideoJpegInfo parseData(@NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseData(): not implemented");
	}

	@Override
	protected int findNextMagicBytes(@NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".findNextMagicBytes(): not implemented");
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull VideoJpegInfo avInfo) {
		throw new RuntimeException(getClass().getSimpleName() + ".readFrameLenFromAvInfo(): not implemented");
	}

}
