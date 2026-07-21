package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvBase;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;

final class PacketSplitter<I extends CodecInfoInterface<I>, FGAV extends FrameGrabberAvBase<?>> {

	private final @NonNull PsLogErrorInterface logErrorMsgInterface;
	private final @NonNull FGAV frameGrabberPtr;
	private final boolean needMagicBytes;
	private final @Nullable PsParseAndConvertDataInterface<I> packetParseAndConvertData;
	private final @Nullable PsParseOnlyDataInterface<I> packetParseOnlyData;
	private final @NonNull PsFindNextMagicBytesInterface packetFindNextMagicBytes;
	private final @Nullable PsGetFrameLenFromAvInfoInterface<I> psGetFrameLenFromAvInfo;

	private final BufferExt remainingInputBuf = new BufferExt();
	private final BufferView remainingInputBv = new BufferView(remainingInputBuf);
	private final TimestampEpochNs stTimestampCurFrame = TimestampEpochNs.ofEmpty();
	private long debugStreamOffset = 0L;

	PacketSplitter(
				@NonNull PsLogErrorInterface logErrorMsgInterface,
				@NonNull FGAV frameGrabberPtr,
				boolean needMagicBytes,
				@Nullable PsParseAndConvertDataInterface<I> packetParseAndConvertData,
				@Nullable PsParseOnlyDataInterface<I> packetParseOnlyData,
				@NonNull PsFindNextMagicBytesInterface packetFindNextMagicBytes,
				@Nullable PsGetFrameLenFromAvInfoInterface<I> psGetFrameLenFromAvInfo
			) {
		if (packetParseAndConvertData != null && packetParseOnlyData != null) {
			throw new IllegalStateException("only one of packetParseAndConvertData or packetParseOnlyData allowed");
		}
		if (packetParseAndConvertData == null && packetParseOnlyData == null) {
			throw new IllegalStateException("need either packetParseAndConvertData or packetParseOnlyData");
		}
		if (! needMagicBytes && packetParseAndConvertData == null) {
			throw new IllegalStateException("need packetParseAndConvertData if needMagicBytes==false");
		}
		if (needMagicBytes && packetParseOnlyData == null) {
			throw new IllegalStateException("need packetParseOnlyData if needMagicBytes==true");
		}

		this.logErrorMsgInterface = logErrorMsgInterface;
		this.frameGrabberPtr = frameGrabberPtr;
		this.needMagicBytes = needMagicBytes;
		this.packetParseAndConvertData = packetParseAndConvertData;
		this.packetParseOnlyData = packetParseOnlyData;
		this.packetFindNextMagicBytes = packetFindNextMagicBytes;
		this.psGetFrameLenFromAvInfo = psGetFrameLenFromAvInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	long getDebugStreamOffset() {
		return debugStreamOffset;
	}

	void getNextSplitPacket(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException, AvInvalidCodecDataException, InputStreamThreadEndedException {
		BufferExt readIntoBufPtr = (needMagicBytes ? remainingInputBuf : buf);
		if (! needMagicBytes || remainingInputBv.getLength() == 0) {
			try {
				frameGrabberPtr.getNextFrame(readIntoBufPtr, stTimestampCurFrame);
			} catch (InputStreamIoException e) {
				throw new InputStreamEosException();
			}
			remainingInputBv.clear();
		}

		//
		try {
			final I tmpInfoObj;
			if (packetParseAndConvertData != null) {
				tmpInfoObj = packetParseAndConvertData.parseAndConvertData(buf);
			} else if (packetParseOnlyData != null) {
				tmpInfoObj = packetParseOnlyData.parseData(remainingInputBv);
			} else {
				throw new IllegalStateException("this should not happen");
			}
			infoObj.copyOf(tmpInfoObj);
		} catch (Exception e) {
			logErrorMsgInterface.logErrorMsg("caught on parsing the entire packet: " + e);
			throw new InputStreamEosException();
		}

		stTimestamp.copyFrom(stTimestampCurFrame);

		/*
		 * IP cameras tend to send, for instance, 'SPS', 'PPS' and a VCL NAL Unit in a single packet.
		 * The same can happen when demuxing from an MKV/MP4/... file.
		 * So we need to find the next magic bytes to split the buffer into multiple NAL Units.
		 */
		if (needMagicBytes && packetParseOnlyData != null) {
			int nextOffset;
			if (psGetFrameLenFromAvInfo != null) {
				nextOffset = psGetFrameLenFromAvInfo.getFrameLen(infoObj);
			} else {
				nextOffset = packetFindNextMagicBytes.findNextMagicBytes(remainingInputBv);
			}
			if (nextOffset < 1 || remainingInputBv.getOffset() + nextOffset == remainingInputBuf.getUsed()) {
				remainingInputBv.copyViewIntoBe(buf);
				remainingInputBuf.clear();
				remainingInputBv.clear();
			} else {
				remainingInputBv.setLength(nextOffset);
				remainingInputBv.copyViewIntoBe(buf);

				remainingInputBv.increaseOffset(nextOffset);
				remainingInputBv.setLength(remainingInputBuf.getUsed() - remainingInputBv.getOffset());

				// parse the new buffer again
				try {
					final I tmpInfoObj = packetParseOnlyData.parseData(new BufferView(buf));
					infoObj.copyOf(tmpInfoObj);
				} catch (Exception e) {
					logErrorMsgInterface.logErrorMsg("caught on parsing the split packet: " + e);
					throw new InputStreamEosException();
				}
			}
		}
		//
		debugStreamOffset += buf.getUsed();
	}

}
