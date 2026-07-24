package org.tsitle.rtsp_server.threads.dataprovider_es;

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
	private final @Nullable PsParseAndConvertDataInterface<I> packetParseAndConvertData;
	private final @Nullable PsParseOnlyDataInterface<I> packetParseOnlyData;
	private final @NonNull PsFindNextMagicBytesInterface packetFindNextMagicBytes;
	private final @Nullable PsGetFrameLenFromAvInfoInterface<I> psGetFrameLenFromAvInfo;

	private final boolean useSplitBuf;

	private final BufferExt remainingInputBuf = new BufferExt();
	private final BufferView remainingInputBv = new BufferView(remainingInputBuf);
	private final TimestampEpochNs stTimestampCurFrame = TimestampEpochNs.ofEmpty();
	private long debugStreamOffset = 0L;

	PacketSplitter(
				@NonNull PsLogErrorInterface logErrorMsgInterface,
				@NonNull FGAV frameGrabberPtr,
				boolean needMagicBytes,
				boolean needConvertData,
				@Nullable PsParseAndConvertDataInterface<I> packetParseAndConvertData,
				@Nullable PsParseOnlyDataInterface<I> packetParseOnlyData,
				@NonNull PsFindNextMagicBytesInterface packetFindNextMagicBytes,
				@Nullable PsGetFrameLenFromAvInfoInterface<I> psGetFrameLenFromAvInfo
			) {
		String tmpErrMsgPrefix = PacketSplitter.class.getSimpleName() + ".ctor(): ";
		if (packetParseAndConvertData != null && packetParseOnlyData != null) {
			throw new IllegalStateException(tmpErrMsgPrefix + "only one of packetParseAndConvertData or packetParseOnlyData allowed");
		}
		if (packetParseAndConvertData == null && packetParseOnlyData == null) {
			throw new IllegalStateException(tmpErrMsgPrefix + "need either packetParseAndConvertData or packetParseOnlyData");
		}
		if (needConvertData && packetParseAndConvertData == null) {
			throw new IllegalStateException(tmpErrMsgPrefix + "need packetParseAndConvertData if needConvertData==true");
		}
		if (packetParseAndConvertData != null && needMagicBytes) {
			throw new IllegalStateException(tmpErrMsgPrefix + "packetParseAndConvertData must be null if needMagicBytes==true");
		}
		if (packetParseAndConvertData != null && psGetFrameLenFromAvInfo != null) {
			throw new IllegalStateException(tmpErrMsgPrefix + "either packetParseAndConvertData or psGetFrameLenFromAvInfo must be null");
		}
		if (! needConvertData && packetParseOnlyData == null) {
			throw new IllegalStateException(tmpErrMsgPrefix + "need packetParseOnlyData if needConvertData==false");
		}

		this.logErrorMsgInterface = logErrorMsgInterface;
		this.frameGrabberPtr = frameGrabberPtr;
		this.packetParseAndConvertData = packetParseAndConvertData;
		this.packetParseOnlyData = packetParseOnlyData;
		this.packetFindNextMagicBytes = packetFindNextMagicBytes;
		this.psGetFrameLenFromAvInfo = psGetFrameLenFromAvInfo;

		//
		this.useSplitBuf = (needMagicBytes || psGetFrameLenFromAvInfo != null);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	long getDebugStreamOffset() {
		return debugStreamOffset;
	}

	void getNextSplitPacket(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException, AvInvalidCodecDataException, InputStreamThreadEndedException {
		BufferExt readIntoBufPtr = (useSplitBuf ? remainingInputBuf : buf);
		if (! useSplitBuf || remainingInputBv.getLength() == 0) {
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
			} else if (! useSplitBuf && packetParseOnlyData != null) {
				tmpInfoObj = packetParseOnlyData.parseData(new BufferView(buf));
			} else if (useSplitBuf && packetParseOnlyData != null) {
				tmpInfoObj = packetParseOnlyData.parseData(remainingInputBv);
			} else {
				throw new IllegalStateException("this should not happen #1");
			}
			infoObj.copyOf(tmpInfoObj);
		} catch (AvInvalidCodecDataException e) {
			throw e;
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
		if (useSplitBuf) {
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
			} else if (packetParseOnlyData != null) {
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
			} else {
				throw new IllegalStateException("this should not happen #2");
			}
		}
		//
		debugStreamOffset += buf.getUsed();
	}

}
