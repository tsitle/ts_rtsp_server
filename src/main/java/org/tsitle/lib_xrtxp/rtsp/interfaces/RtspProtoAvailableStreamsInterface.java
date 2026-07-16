package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoElementaryStreamSource;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdEsSourceNotFoundException;

import java.net.URI;
import java.util.Optional;

public interface RtspProtoAvailableStreamsInterface {

	/**
	 * Check if the given Input Source ID exists.
	 * @param idInputSource Input Source ID
	 * @return True if the Input Source ID exists, false otherwise
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
	 * Get the first (enabled) video Elementary-Stream Source for the Input Source.
	 * @param idInputSource Input Source ID
	 * @return Elementary-Stream Source
	 */
	Optional<RtspProtoElementaryStreamSource> getFirstVideoEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			);

	/**
	 * Get the first (enabled) audio Elementary-Stream Source for the Input Source.
	 * @param idInputSource Input Source ID
	 * @return Elementary-Stream Source
	 */
	Optional<RtspProtoElementaryStreamSource> getFirstAudioEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			);

	// -----------------------------------------------------------------------------------------------------------------

	record ElementaryStreamSourceInfo(
			@NonNull RtpPacketType codec,
			boolean isSourceFromFile,
			boolean isSourceFromMq,
			boolean isSourceFromDemuxedMs,
			@NonNull URI inputUri,
			byte audioChannelCount,
			@NonNull SampleRateEnum audioSampleRate,
			boolean isAudioBigEndian,
			int audioAacSpf,
			@NonNull String audioAacHexCfg,
			@NonNull FrameRateEnum videoFps
		) { }

	/**
	 * Get information about the given Elementary-Stream Source.
	 * @param idEsSource Elementary-Stream Source ID
	 * @return Elementary-Stream Source information
	 * @throws RtspProtoIdEsSourceNotFoundException If the Elementary-Stream Source ID is not found
	 */
	@NonNull ElementaryStreamSourceInfo getElementaryStreamSourceInfo(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException;

	/**
	 * Get audio samples per frame (matching the video frame rate) as required for RTP for the given audio Elementary-Stream Source.
	 * @param idEsSource Elementary-Stream Source ID
	 * @param videoFps Frames per second value of the corresponding video sub-stream
	 * @return Samples per frame
	 * @throws RtspProtoIdEsSourceNotFoundException If the Elementary-Stream Source ID is not found
	 */
	int getElementaryStreamSourceRtpAudioSamplesPerFrame(@NonNull RtspProtoIdEsSource idEsSource, double videoFps)
			throws RtspProtoIdEsSourceNotFoundException;

}
