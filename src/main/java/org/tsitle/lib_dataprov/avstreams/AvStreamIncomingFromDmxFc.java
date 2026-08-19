package org.tsitle.lib_dataprov.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_dataprov.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;
import org.tsitle.lib_dataprov.threads_dmxFc.TdpDemuxFcReadNextAvPacketInterface;

import java.util.Optional;

public final class AvStreamIncomingFromDmxFc extends AvStreamIncomingBase {

	private final boolean isVideo;
	private final @NonNull TdpDemuxFcReadNextAvPacketInterface readNextAvPacketInterface;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param idEsSource Elementary-Stream Source identifier
	 * @param isVideo Is this a video stream? (if false, it is an audio stream)
	 * @param readNextAvPacketInterface 'Demuxer: Read next A/V packet' instance
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncomingFromDmxFc(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdEsSource idEsSource,
				boolean isVideo,
				@NonNull TdpDemuxFcReadNextAvPacketInterface readNextAvPacketInterface
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

	@Override
	public Optional<Double> getVideoFps() {
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		haveEos = true;
	}

}
