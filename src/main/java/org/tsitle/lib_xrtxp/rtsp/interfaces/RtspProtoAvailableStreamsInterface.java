package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
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
			@NonNull RtspProtoEsSourceType esSourceType,
			@NonNull URI inputUri,
			double durationSecs,
			byte audioChannelCount,
			@NonNull SampleRateEnum audioSampleRate,
			int audioSamplesPerFrame,
			boolean isAudioPcmBigEndian,
			@NonNull ExtradataContainerHex audioAacHexCfg,
			@NonNull FrameRateEnum videoFps,
			@NonNull ExtradataContainerSdp videoExtraB64Cfg
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
	 * Compute the virtual framerate as required for RTP for the given Elementary-Stream Source.
	 * @param idEsSource Elementary-Stream Source ID
	 * @return Frames per second or -1.0 if the framerate cannot be computed
	 * @throws RtspProtoIdEsSourceNotFoundException If the Elementary-Stream Source ID is not found
	 */
	double computeElementaryStreamSource_virtualFps(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException;

	/**
	 * Get the number of audio samples per frame as required for RTP for the given Elementary-Stream Source.
	 * @param idEsSource Elementary-Stream Source ID
	 * @return Samples per frame or -1 if the value is not available
	 * @throws RtspProtoIdEsSourceNotFoundException If the Elementary-Stream Source ID is not found
	 */
	int getElementaryStreamSource_samplesPerFrame(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException;

}
