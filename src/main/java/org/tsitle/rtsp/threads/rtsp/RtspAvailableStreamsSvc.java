package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspConfigInputSource;
import org.tsitle.rtsp.config.RtspConfigStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoIdStreamSourceNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class RtspAvailableStreamsSvc implements RtspProtoAvailableStreamsInterface {

	private final @NonNull RtspConfig rtspConfig;

	private final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMap = new HashMap<>();
	private final @NonNull Map<@NonNull RtspProtoIdStreamSource, @NonNull RtspProtoStreamSource> streamSourceMap = new HashMap<>();

	public RtspAvailableStreamsSvc(@NonNull RtspConfig rtspConfig) {
		this.rtspConfig = rtspConfig;

		//
		Map<Integer, RtspProtoIdStreamSource> tmpStreamSourceIdMap = new HashMap<>();
		for (Integer tmpSsIdInt : rtspConfig.getStreamSourceIds()) {
			Optional<RtspConfigStreamSource> tmpOptSsObjInp = rtspConfig.getStreamSourceObj(tmpSsIdInt);
			if (tmpOptSsObjInp.isEmpty()) {
				continue;  // this should never happen
			}
			RtspConfigStreamSource tmpSsObjInp = tmpOptSsObjInp.get();
			RtspProtoIdStreamSource tmpId = tmpSsObjInp.getIdAsProtoId().clone();
			RtspProtoStreamSource tmpSsObjOut = new RtspProtoStreamSource();
			tmpSsObjOut.setIdStreamSource(tmpId);
			tmpSsObjOut.setEnabled(tmpSsObjInp.getEnabled());

			this.streamSourceMap.put(tmpId, tmpSsObjOut);

			tmpStreamSourceIdMap.put(tmpSsIdInt, tmpId.clone());
		}

		//
		for (String tmpIsIdStr : rtspConfig.getInputSourceIds()) {
			Optional<RtspConfigInputSource> tmpOptIsObjInp = rtspConfig.getInputSourceObj(tmpIsIdStr);
			if (tmpOptIsObjInp.isEmpty()) {
				continue;  // this should never happen
			}
			RtspConfigInputSource tmpIsObjInp = tmpOptIsObjInp.get();
			RtspProtoIdInputSource tmpId = tmpIsObjInp.getIdAsProtoId().clone();
			RtspProtoInputSource tmpIsObjOut = new RtspProtoInputSource();
			tmpIsObjOut.setIdInputSource(tmpId);
			tmpIsObjOut.setEnabled(tmpIsObjInp.getEnabled());
			tmpIsObjOut.setNeedsAuthentication(tmpIsObjInp.getNeedsAuthentication());
			tmpIsObjOut.setAllowedUserAccountGroups(tmpIsObjInp.getAllowedUserAccountGroups());
			tmpIsObjOut.setNeedsEncryption(tmpIsObjInp.getNeedsEncryption());

			for (Integer tmpSsIdInp : tmpIsObjInp.getStreamSourceIds()) {
				if (! tmpStreamSourceIdMap.containsKey(tmpSsIdInp)) {
					continue;
				}
				tmpIsObjOut.putIdStreamSource(
						tmpStreamSourceIdMap.get(tmpSsIdInp).clone()
					);
			}

			this.inputSourceMap.put(tmpId, tmpIsObjOut);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean existsInputSourceId(@NonNull RtspProtoIdInputSource idInputSource) {
		return inputSourceMap.containsKey(idInputSource);
	}

	@Override
	public @NonNull RtspProtoInputSource getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource)
			throws RtspProtoIdInputSourceNotFoundException {
		if (! inputSourceMap.containsKey(idInputSource)) {
			throw new RtspProtoIdInputSourceNotFoundException(idInputSource.toString());
		}
		return inputSourceMap.get(idInputSource);
	}

	@Override
	public Optional<RtspProtoStreamSource> getFirstVideoStreamSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			) {
		return getFirstOfKindStreamSourceObj(idInputSource, true);
	}

	@Override
	public Optional<RtspProtoStreamSource> getFirstAudioStreamSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			) {
		return getFirstOfKindStreamSourceObj(idInputSource, false);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull StreamSourceInfo getStreamSourceInfo(@NonNull RtspProtoIdStreamSource idStreamSource)
			throws RtspProtoIdStreamSourceNotFoundException {
		RtspConfigStreamSource tmpCfgSs = getConfigStreamSourceObj(idStreamSource);

		try {
			return new StreamSourceInfo(
					tmpCfgSs.getCodec(),
					tmpCfgSs.getIsSourceFromFile(),
					tmpCfgSs.getIsSourceFromMq(),
					tmpCfgSs.getInputUri(),
					tmpCfgSs.getAudioChannelCount(),
					tmpCfgSs.getAudioSamplerateHz(),
					tmpCfgSs.getIsAudioBigEndian(),
					tmpCfgSs.getAacSamplesPerFrame(),
					tmpCfgSs.getAacAudioSpecificConfigHexStr(),
					tmpCfgSs.getVideoFps()
				);
		} catch (IllegalStateException e) {
			throw new RtspProtoIdStreamSourceNotFoundException("ss='" + idStreamSource.getIdStr() + "': " + e.getMessage());
		}
	}

	@Override
	public int getStreamSourceRtpAudioSamplesPerFrame(@NonNull RtspProtoIdStreamSource idStreamSource, double videoFps)
			throws RtspProtoIdStreamSourceNotFoundException {
		RtspConfigStreamSource tmpCfgSs = getConfigStreamSourceObj(idStreamSource);

		try {
			return tmpCfgSs.getRtpAudioSamplesPerFrame(videoFps);
		} catch (IllegalStateException e) {
			throw new RtspProtoIdStreamSourceNotFoundException("ss='" + idStreamSource.getIdStr() + "': " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the first enabled audio or video Stream Source for the Input Source.
	 * @param idInputSource Input Source ID
	 * @param isVideo Get video Stream Source if true, audio Stream Source otherwise
	 * @return Stream Source
	 */
	private Optional<RtspProtoStreamSource> getFirstOfKindStreamSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource,
				boolean isVideo
			) {
		RtspProtoInputSource tmpInputSource;
		try {
			tmpInputSource = getInputSourceObj(idInputSource);
		} catch (RtspProtoIdInputSourceNotFoundException e) {
			return Optional.empty();
		}
		for (RtspProtoIdStreamSource tmpSsId : tmpInputSource.getStreamSourceIds()) {
			if (! streamSourceMap.containsKey(tmpSsId)) {
				continue;  // this should never happen
			}
			RtspConfigStreamSource streamSourceObj;
			try {
				streamSourceObj = getConfigStreamSourceObj(tmpSsId);
			} catch (RtspProtoIdStreamSourceNotFoundException e) {
				return Optional.empty();
			}
			if (! streamSourceObj.getEnabled()) {
				continue;
			}
			if ((isVideo && streamSourceObj.getCodec().isVideo()) ||
					(! isVideo && streamSourceObj.getCodec().isAudio())) {
				return Optional.of(streamSourceMap.get(tmpSsId));
			}
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspConfigStreamSource getConfigStreamSourceObj(@NonNull RtspProtoIdStreamSource idStreamSource)
			throws RtspProtoIdStreamSourceNotFoundException {
		if (! streamSourceMap.containsKey(idStreamSource)) {
			throw new RtspProtoIdStreamSourceNotFoundException(idStreamSource.getIdStr());
		}
		Optional<RtspConfigStreamSource> tmpOptCfgSs = rtspConfig.getStreamSourceObj(idStreamSource);
		if (tmpOptCfgSs.isEmpty()) {
			throw new RtspProtoIdStreamSourceNotFoundException(idStreamSource.getIdStr());
		}
		return tmpOptCfgSs.get();
	}

}
