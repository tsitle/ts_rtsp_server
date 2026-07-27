package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;
import org.tsitle.rtsp_server.threads.dataprovider_demux.TdpDemuxReadNextAvPacketInterface;

public final class AvStreamIncomingFromDemuxMs extends AvStreamIncomingBase {

	private final boolean isVideo;
	private final @NonNull TdpDemuxReadNextAvPacketInterface readNextAvPacketInterface;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param idEsSource Elementary-Stream Source identifier
	 * @param isVideo Is this a video stream? (if false, it is an audio stream)
	 * @param readNextAvPacketInterface 'Demuxer: Read next A/V packet' instance
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncomingFromDemuxMs(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdEsSource idEsSource,
				boolean isVideo,
				@NonNull TdpDemuxReadNextAvPacketInterface readNextAvPacketInterface
			) throws AvCannotOpenInputException {
		super(logMsgInterface, idEsSource);

		this.isVideo = isVideo;
		this.readNextAvPacketInterface = readNextAvPacketInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void readFrame(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException {
		stTimestamp.clear();
		buf.clear();
		if (haveEos) {
			throw new InputStreamEosException();
		}
		try {
			if (isVideo) {
				readNextAvPacketInterface.readNextPacketVideo(buf, stTimestamp);
			} else {
				readNextAvPacketInterface.readNextPacketAudio(buf, stTimestamp);
			}
		} catch (InputStreamEosException | InputStreamThreadEndedException e) {
			haveEos = true;
			throw e;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Rewinds the stream to the beginning
	 * @throws AvCannotOpenInputException If the input stream cannot be reopened
	 */
	@Override
	public void rewind() throws AvCannotOpenInputException {
		throw new AvCannotOpenInputException("Cannot rewind a Demuxed sub-stream");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		haveEos = true;
	}

}
