package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdEsSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoClientCredentials;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoElementaryStreamSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.rtsp_server.config.RtspConfigElementaryStreamSourceOLDDD;
import org.tsitle.rtsp_server.config.RtspConfigInputSourceOLDDD;
import org.tsitle.rtsp_server.config.RtspConfigOLDDD;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class RtspAvailableStreamsSvcOLDDD implements RtspProtoAvailableStreamsInterface {

	private final @NonNull RtspConfigOLDDD rtspConfig;

	private final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMap = new HashMap<>();
	private final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> esSourceMap = new HashMap<>();

	public RtspAvailableStreamsSvcOLDDD(@NonNull RtspConfigOLDDD rtspConfig) {
		this.rtspConfig = rtspConfig;

		//
		Map<Integer, RtspProtoIdEsSource> tmpStreamSourceIdMap = new HashMap<>();
		for (Integer tmpSsIdInt : rtspConfig.getElementaryStreamSourceIds()) {
			Optional<RtspConfigElementaryStreamSourceOLDDD> tmpOptSsObjInp = rtspConfig.getElementaryStreamSourceObj(tmpSsIdInt);
			if (tmpOptSsObjInp.isEmpty()) {
				continue;  // this should never happen
			}
			RtspConfigElementaryStreamSourceOLDDD tmpSsObjInp = tmpOptSsObjInp.get();
			RtspProtoIdEsSource tmpId = tmpSsObjInp.getIdAsProtoId().clone();
			RtspProtoElementaryStreamSource tmpSsObjOut = new RtspProtoElementaryStreamSource();
			tmpSsObjOut.setIdEsSource(tmpId);
			tmpSsObjOut.setEnabled(tmpSsObjInp.getEnabled());

			this.esSourceMap.put(tmpId, tmpSsObjOut);

			tmpStreamSourceIdMap.put(tmpSsIdInt, tmpId.clone());
		}

		//
		for (String tmpIsIdStr : rtspConfig.getInputSourceIds()) {
			Optional<RtspConfigInputSourceOLDDD> tmpOptIsObjInp = rtspConfig.getInputSourceObj(tmpIsIdStr);
			if (tmpOptIsObjInp.isEmpty()) {
				continue;  // this should never happen
			}
			RtspConfigInputSourceOLDDD tmpIsObjInp = tmpOptIsObjInp.get();
			RtspProtoIdInputSource tmpId = tmpIsObjInp.getIdAsProtoId().clone();
			RtspProtoInputSource tmpIsObjOut = new RtspProtoInputSource();
			tmpIsObjOut.setIdInputSource(tmpId);
			tmpIsObjOut.setEnabled(tmpIsObjInp.getEnabled());
			tmpIsObjOut.setNeedsAuthentication(tmpIsObjInp.getNeedsAuthentication());
			tmpIsObjOut.setAllowedUserAccountGroups(tmpIsObjInp.getAllowedUserAccountGroups());
			tmpIsObjOut.setNeedsEncryption(tmpIsObjInp.getNeedsEncryption());

			for (Integer tmpSsIdInp : tmpIsObjInp.getElementaryStreamSourceIds()) {
				if (! tmpStreamSourceIdMap.containsKey(tmpSsIdInp)) {
					continue;
				}
				tmpIsObjOut.putIdEsSource(
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
	public Optional<RtspProtoElementaryStreamSource> getFirstVideoEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			) {
		return getFirstOfKindEsSourceObj(idInputSource, true);
	}

	@Override
	public Optional<RtspProtoElementaryStreamSource> getFirstAudioEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			) {
		return getFirstOfKindEsSourceObj(idInputSource, false);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoEsSourceExpandedInfo getElementaryStreamSourceExpInfo(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		RtspConfigElementaryStreamSourceOLDDD tmpCfgSs = getConfigEsSourceObj(idEsSource);

		try {
			return new RtspProtoEsSourceExpandedInfo(
					-1,
					tmpCfgSs.getCodec(),
					tmpCfgSs.getSourceType(),
					tmpCfgSs.getInputUri(),
					RtspProtoClientCredentials.ofEmpty(),
					tmpCfgSs.getDurationSecs(),
					tmpCfgSs.getAudioChannelCount(),
					tmpCfgSs.getAudioSamplerate(),
					tmpCfgSs.getAudioSamplesPerFrame(),
					tmpCfgSs.getIsPcmAudioBigEndian(),
					tmpCfgSs.getAacAudioSpecificConfigHex(),
					tmpCfgSs.getVideoFps(),
					tmpCfgSs.getVideoExtraB64Cfg()
				);
		} catch (IllegalStateException e) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource) +
					": " + e.getMessage());
		}
	}

	@Override
	public double computeElementaryStreamSource_virtualFps(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		RtspConfigElementaryStreamSourceOLDDD tmpCfgSs = getConfigEsSourceObj(idEsSource);

		try {
			return tmpCfgSs.computeAudioVirtualFps();
		} catch (IllegalStateException e) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource) +
					": " + e.getMessage());
		}
	}

	@Override
	public int getElementaryStreamSource_samplesPerFrame(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		RtspConfigElementaryStreamSourceOLDDD tmpCfgSs = getConfigEsSourceObj(idEsSource);

		try {
			return tmpCfgSs.getAudioSamplesPerFrame();
		} catch (IllegalStateException e) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource) +
					": " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the first enabled audio or video Elementary-Stream Source for the Input Source.
	 * @param idInputSource Input Source ID
	 * @param isVideo Get video source if true, audio source otherwise
	 * @return Elementary-Stream Source
	 */
	private Optional<RtspProtoElementaryStreamSource> getFirstOfKindEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource,
				boolean isVideo
			) {
		RtspProtoInputSource tmpInputSource;
		try {
			tmpInputSource = getInputSourceObj(idInputSource);
		} catch (RtspProtoIdInputSourceNotFoundException e) {
			return Optional.empty();
		}
		for (RtspProtoIdEsSource tmpSsId : tmpInputSource.getEsSourceIds()) {
			if (! esSourceMap.containsKey(tmpSsId)) {
				continue;  // this should never happen
			}
			RtspConfigElementaryStreamSourceOLDDD streamSourceObj;
			try {
				streamSourceObj = getConfigEsSourceObj(tmpSsId);
			} catch (RtspProtoIdEsSourceNotFoundException e) {
				return Optional.empty();
			}
			if (! streamSourceObj.getEnabled()) {
				continue;
			}
			if ((isVideo && streamSourceObj.getCodec().isVideo()) ||
					(! isVideo && streamSourceObj.getCodec().isAudio())) {
				return Optional.of(esSourceMap.get(tmpSsId));
			}
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspConfigElementaryStreamSourceOLDDD getConfigEsSourceObj(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		if (! esSourceMap.containsKey(idEsSource)) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		Optional<RtspConfigElementaryStreamSourceOLDDD> tmpOptCfgSs = rtspConfig.getElementaryStreamSourceObj(idEsSource);
		if (tmpOptCfgSs.isEmpty()) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		return tmpOptCfgSs.get();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String getEsSourceIdForExcMsg(@NonNull RtspProtoIdEsSource idEsSource) {
		return "esSrc='" + idEsSource.getIdStr().orElse("-unset-") + "'";
	}

}
