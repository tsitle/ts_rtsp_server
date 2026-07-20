package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvBase;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;

final class PacketSplitter<I extends CodecInfoInterface<I>, FGAV extends FrameGrabberAvBase<?>> {

	private final @NonNull PsLogErrorInterface logErrorMsgInterface;
	private final @NonNull FGAV frameGrabberPtr;
	private final boolean needMagicBytes;
	private final @NonNull PsParseAndConvertDataInterface<I> packetParseAndConvertData;
	private final @NonNull PsFindNextMagicBytesInterface packetFindNextMagicBytes;

	private final BufferExt remainingInputBuf = new BufferExt();
	private final TimestampEpochNs stTimestampCurFrame = TimestampEpochNs.ofEmpty();

	PacketSplitter(
				@NonNull PsLogErrorInterface logErrorMsgInterface,
				@NonNull FGAV frameGrabberPtr,
				boolean needMagicBytes,
				@NonNull PsParseAndConvertDataInterface<I> packetParseAndConvertData,
				@NonNull PsFindNextMagicBytesInterface packetFindNextMagicBytes
			) {
		this.logErrorMsgInterface = logErrorMsgInterface;
		this.frameGrabberPtr = frameGrabberPtr;
		this.needMagicBytes = needMagicBytes;
		this.packetParseAndConvertData = packetParseAndConvertData;
		this.packetFindNextMagicBytes = packetFindNextMagicBytes;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void getNextSplitPacket(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException, AvInvalidCodecDataException, InputStreamThreadEndedException {
		//do {
			BufferExt readIntoBufPtr = (needMagicBytes ? remainingInputBuf : buf);
			if (! needMagicBytes || remainingInputBuf.isEmpty()) {
				try {
					frameGrabberPtr.getNextFrame(readIntoBufPtr, stTimestampCurFrame);
				} catch (InputStreamIoException e) {
					throw new InputStreamEosException();
				}
			}
			//
			try {
				final I tmpInfoObj = packetParseAndConvertData.parseAndConvertData(readIntoBufPtr);
				infoObj.copyOf(tmpInfoObj);
			} catch (Exception e) {
				logErrorMsgInterface.logErrorMsg("caught: " + e);
				throw new InputStreamEosException();
			}

			stTimestamp.copyFrom(stTimestampCurFrame);

			/*
			 * IP cameras tend to send, for instance, 'SPS', 'PPS' and a VCL NAL Unit in a single packet.
			 * So we need to find the next magic bytes to split the buffer into multiple NAL Units.
			 */
			if (needMagicBytes) {
				int nextOffset = packetFindNextMagicBytes.findNextMagicBytes(remainingInputBuf);
				if (nextOffset < 1) {
					buf.copyOf(remainingInputBuf);
					remainingInputBuf.clear();
				} else {
					buf.copyOf(remainingInputBuf, 0, nextOffset);
					BufferExt tmpBuf = new BufferExt();
					tmpBuf.copyOf(remainingInputBuf, nextOffset, remainingInputBuf.getUsed() - nextOffset);
					remainingInputBuf.copyOf(tmpBuf);
					// parse the new buffer again
					try {
						final I tmpInfoObj = packetParseAndConvertData.parseAndConvertData(buf);
						infoObj.copyOf(tmpInfoObj);
					} catch (Exception e) {
						logErrorMsgInterface.logErrorMsg("caught: " + e);
						throw new InputStreamEosException();
					}
				}
			}
			//
			//debugStreamOffset += buf.getUsed();  // @TODO
		//} while (! haveAllRequiredMetadataPackets);
	}

}
