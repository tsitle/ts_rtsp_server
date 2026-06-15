package org.tsitle.rtsp.threads.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoIdStreamSourceNotFoundException;

import java.net.URI;
import java.util.Optional;

public interface RtspProtoAvailableStreamsInterface {

	/**
	 * Check if the given Input Source ID exists.
	 * @param idInputSource Input Source ID
	 * @return True if the Input Source ID exists, false otherwise
	 */
	boolean existsInputSourceId(@NonNull RtspProtoIdInputSource idInputSource);

	/**
	 * Get the Input Source object for the given ID.
	 * @param idInputSource Input Source ID
	 * @return Input Source
	 * @throws RtspProtoIdInputSourceNotFoundException If the Input Source ID is not found
	 */
	@NonNull RtspProtoInputSource getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource)
			throws RtspProtoIdInputSourceNotFoundException;

	/**
	 * Get the first (enabled) video Stream Source for the Input Source.
	 * @param idInputSource Input Source ID
	 * @return Stream Source
	 */
	Optional<RtspProtoStreamSource> getFirstVideoStreamSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			);

	/**
	 * Get the first (enabled) audio Stream Source for the Input Source.
	 * @param idInputSource Input Source ID
	 * @return Stream Source
	 */
	Optional<RtspProtoStreamSource> getFirstAudioStreamSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			);

	// -----------------------------------------------------------------------------------------------------------------

	record StreamSourceInfo(
			@NonNull RtpPacketType codec,
			boolean isSourceFromFile,
			boolean isSourceFromMq,
			@NonNull URI inputUri,
			byte audioChannelCount,
			int audioSampleRateHz,
			boolean isAudioBigEndian,
			int audioAacSpf,
			@NonNull String audioAacHexCfg,
			double videoFps
		) { }

	/**
	 * Get information about the given Stream Source.
	 * @param idStreamSource Stream Source ID
	 * @return Stream Source information
	 * @throws RtspProtoIdStreamSourceNotFoundException If the Stream Source ID is not found
	 */
	@NonNull StreamSourceInfo getStreamSourceInfo(@NonNull RtspProtoIdStreamSource idStreamSource)
			throws RtspProtoIdStreamSourceNotFoundException;

	/**
	 * Get audio samples per frame (matching the video frame rate) as required for RTP for the given audio Stream Source.
	 * @param idStreamSource Stream Source ID
	 * @param videoFps Stream Source ID
	 * @return Samples per frame
	 * @throws RtspProtoIdStreamSourceNotFoundException If the Stream Source ID is not found
	 */
	int getStreamSourceRtpAudioSamplesPerFrame(@NonNull RtspProtoIdStreamSource idStreamSource, double videoFps)
			throws RtspProtoIdStreamSourceNotFoundException;

}
