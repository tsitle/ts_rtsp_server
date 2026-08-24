package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.rtsp_server.availstreams.RtspAsSvcInputData;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsSs;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsStream;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.util.*;

final class StreamsCfgMapper {

	private static class Mappings<INTERNALIDTYPE, CFGTYPE> {
		final @NonNull Map<@NonNull String, CFGTYPE> mapExternalIdToCfgObjPrev = new HashMap<>();
		final @NonNull Map<@NonNull String, CFGTYPE> mapExternalIdToCfgObjCur = new HashMap<>();
		final @NonNull Map<@NonNull String, INTERNALIDTYPE> mapExternalToInternalIdPrev = new HashMap<>();
		final @NonNull Map<@NonNull String, INTERNALIDTYPE> mapExternalToInternalIdCur = new HashMap<>();

		final @NonNull Set<@NonNull String> deltaAddedIds = new HashSet<>();
		final @NonNull Set<@NonNull String> deltaModifiedIds = new HashSet<>();
		final @NonNull Set<@NonNull String> deltaDeletedIds = new HashSet<>();

		void clearCurrent() {
			mapExternalIdToCfgObjCur.clear();
			mapExternalToInternalIdCur.clear();
		}

		void clearDeltaIds() {
			deltaAddedIds.clear();
			deltaModifiedIds.clear();
			deltaDeletedIds.clear();
		}

		boolean haveChanges() {
			return (! (deltaAddedIds.isEmpty() && deltaModifiedIds.isEmpty() && deltaDeletedIds.isEmpty()));
		}

		void moveCurrentToPrevious() {
			mapExternalIdToCfgObjPrev.clear();
			mapExternalIdToCfgObjPrev.putAll(mapExternalIdToCfgObjCur);
			mapExternalIdToCfgObjCur.clear();

			mapExternalToInternalIdPrev.clear();
			mapExternalToInternalIdPrev.putAll(mapExternalToInternalIdCur);
			mapExternalToInternalIdCur.clear();
		}
	}

	private static class DmxVirtualEses {
		final @NonNull Map<@NonNull String, @NonNull RtspProtoIdInputSource> mapExternalIsIdToInternalVirtIsId = new HashMap<>();
		final @NonNull Map<@NonNull String, @NonNull RtspProtoIdEsSource> mapVirtExternalEsIdToInternal = new HashMap<>();

		final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspSrvConfigStreamsStream> mapVirtIsIdToCfgObj = new HashMap<>();
		final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSs> mapVirtEsIdToCfgObj = new HashMap<>();

		final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapVirtEsIdToEsei = new HashMap<>();
	}

	private static class RawFileOrMqEsMetaInfo {
		final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapEsIdToEsei = new HashMap<>();
	}

	private final @NonNull LogMsgInterface logMsgInterface;

	private final Mappings<@NonNull RtspProtoIdInputSource, @NonNull RtspSrvConfigStreamsStream> mappingsIs = new Mappings<>();
	private final Mappings<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSs> mappingsEs = new Mappings<>();

	private final @NonNull DmxVirtualEses dmxVirtualEses = new DmxVirtualEses();
	private final @NonNull RawFileOrMqEsMetaInfo rawFileEsMetaInfo = new RawFileOrMqEsMetaInfo();
	private final @NonNull RawFileOrMqEsMetaInfo mqEsMetaInfo = new RawFileOrMqEsMetaInfo();

	StreamsCfgMapper(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	Optional<Boolean> mapStreamsCfg(
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> currentSubStreams,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> currentStreams,
				@NonNull RtspAsSvcInputData asSvcInputData
			) {
		internalMapXxCfg(RtspProtoIdInputSource.class, RtspSrvConfigStreamsStream.class, currentStreams, mappingsIs);
		internalMapXxCfg(RtspProtoIdEsSource.class, RtspSrvConfigStreamsSs.class, currentSubStreams, mappingsEs);

		// compare previous vs. current configs
		compareXxCfgs(mappingsIs);
		compareXxCfgs(mappingsEs);
		compareSubStreamsPerStream();

		//
		//debugPrintStreams();

		//
		boolean tmpResB = updateVirtualEses();
		if (! tmpResB) {
			return Optional.empty();
		}

		//
		updateRawFileOrMqMetaInfo(true);

		//
		updateRawFileOrMqMetaInfo(false);

		// --------------------------------------------------------

		populateAsSvcInputData(asSvcInputData);

		// --------------------------------------------------------

		// move current configs to previous
		mappingsIs.moveCurrentToPrevious();
		mappingsEs.moveCurrentToPrevious();

		//
		return Optional.of(mappingsIs.haveChanges());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static <INTERNALIDTYPE, CFGTYPE> void internalMapXxCfg(
				@NonNull Class<INTERNALIDTYPE> idType,
				@NonNull Class<CFGTYPE> cfgType,
				@NonNull Map<@NonNull String, @NonNull CFGTYPE> allXxtreams,
				@NonNull Mappings<@NonNull INTERNALIDTYPE, @NonNull CFGTYPE> mappings
			) {
		mappings.clearCurrent();
		mappings.clearDeltaIds();
		for (Map.Entry<String, CFGTYPE> entry : allXxtreams.entrySet()) {
			if (idType == RtspProtoIdInputSource.class) {
				RtspProtoIdInputSource tmpIntId = StreamsCfgIdMapperHelper.computeInternalIsId(entry.getKey());
				mappings.mapExternalToInternalIdCur.put(entry.getKey(), idType.cast(tmpIntId));
			} else if (idType == RtspProtoIdEsSource.class) {
				RtspProtoIdEsSource tmpIntId = StreamsCfgIdMapperHelper.computeInternalEsId(entry.getKey());
				mappings.mapExternalToInternalIdCur.put(entry.getKey(), idType.cast(tmpIntId));
			} else {
				throw new RuntimeException(StreamsCfgMapper.class.getSimpleName() + ": Unsupported ID type: " + idType);
			}
			//
			if (! mappings.mapExternalToInternalIdPrev.containsKey(entry.getKey())) {
				mappings.deltaAddedIds.add(entry.getKey());
			}
			//
			if (cfgType == RtspSrvConfigStreamsStream.class) {
				RtspSrvConfigStreamsStream casted = (RtspSrvConfigStreamsStream)cfgType.cast(entry.getValue());
				mappings.mapExternalIdToCfgObjCur.put(entry.getKey(), cfgType.cast(casted.clone()));
			} else if (cfgType == RtspSrvConfigStreamsSs.class) {
				RtspSrvConfigStreamsSs casted = (RtspSrvConfigStreamsSs)cfgType.cast(entry.getValue());
				mappings.mapExternalIdToCfgObjCur.put(entry.getKey(), cfgType.cast(casted.clone()));
			} else {
				throw new RuntimeException(StreamsCfgMapper.class.getSimpleName() + ": Unsupported CFG type: " + cfgType);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static <INTERNALIDTYPE, CFGTYPE> void compareXxCfgs(@NonNull Mappings<@NonNull INTERNALIDTYPE, @NonNull CFGTYPE> mappings) {
		for (Map.Entry<String, CFGTYPE> entry : mappings.mapExternalIdToCfgObjPrev.entrySet()) {
			if (mappings.mapExternalIdToCfgObjCur.containsKey(entry.getKey())) {
				continue;
			}
			// --> deleted
			mappings.deltaDeletedIds.add(entry.getKey());
		}
		//
		for (Map.Entry<String, CFGTYPE> entry : mappings.mapExternalIdToCfgObjCur.entrySet()) {
			if (mappings.deltaAddedIds.contains(entry.getKey())) {
				continue;
			}
			if (entry.getValue().equals(mappings.mapExternalIdToCfgObjPrev.get(entry.getKey()))) {
				// --> unmodified
				continue;
			}
			// --> modified
			mappings.deltaModifiedIds.add(entry.getKey());
		}
	}

	private void compareSubStreamsPerStream() {
		for (Map.Entry<String, RtspSrvConfigStreamsStream> entryStream : mappingsIs.mapExternalIdToCfgObjCur.entrySet()) {
			if (mappingsIs.deltaAddedIds.contains(entryStream.getKey()) ||
					mappingsIs.deltaModifiedIds.contains(entryStream.getKey())) {
				continue;
			}
			// --> unchanged stream cfg
			for (String extSsId : entryStream.getValue().getSubStreamIds()) {
				if (! mappingsEs.mapExternalIdToCfgObjCur.containsKey(extSsId)) {
					continue;  // this should never happen
				}
				if (! (mappingsEs.deltaAddedIds.contains(extSsId) ||
						mappingsEs.deltaModifiedIds.contains(extSsId) ||
						mappingsEs.deltaDeletedIds.contains(extSsId))) {
					continue;
				}
				// --> added/modified/deleted sub-stream cfg
				mappingsIs.deltaModifiedIds.add(entryStream.getKey());
				break;
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/*private void debugPrintStreams() {
		final String FNC_NAME = getClass().getSimpleName() + ".debugPrintStreams()";

		for (String extId : mappingsIs.deltaDeletedIds) {
			logDebug(FNC_NAME, "stream deleted: " + extId);
		}
		for (Map.Entry<String, RtspSrvConfigStreamsStreamNg> entry : mappingsIs.mapExternalIdToCfgObjCur.entrySet()) {
			if (mappingsIs.deltaAddedIds.contains(entry.getKey())) {
				logDebug(FNC_NAME, "stream added: " + entry.getKey());
				continue;
			}
			if (mappingsIs.deltaModifiedIds.contains(entry.getKey())) {
				logDebug(FNC_NAME, "stream modified: " + entry.getKey());
				continue;
			}
			//logDebug(FNC_NAME, "stream unchanged: " + entry.getKey());
		}
	}*/

	// -----------------------------------------------------------------------------------------------------------------

	private boolean updateVirtualEses() {
		// delete virtual ESs for deleted/new/modified streams
		Set<@NonNull String> addedAndModAndDelStreamIds = new HashSet<>();
		addedAndModAndDelStreamIds.addAll(mappingsIs.deltaAddedIds);
		addedAndModAndDelStreamIds.addAll(mappingsIs.deltaModifiedIds);
		addedAndModAndDelStreamIds.addAll(mappingsIs.deltaDeletedIds);
		for (String extRealIsId : addedAndModAndDelStreamIds) {
			RtspProtoIdInputSource virtIsId = dmxVirtualEses.mapExternalIsIdToInternalVirtIsId.get(extRealIsId);
			if (virtIsId == null) {
				continue;
			}
			RtspSrvConfigStreamsStream virtIsObj = dmxVirtualEses.mapVirtIsIdToCfgObj.get(virtIsId);
			if (virtIsObj == null) {
				continue;
			}
			for (String virtExtEsId : virtIsObj.getSubStreamIds()) {
				RtspProtoIdEsSource tmpIdEs = StreamsCfgIdMapperHelper.computeInternalEsId(virtExtEsId);
				dmxVirtualEses.mapVirtExternalEsIdToInternal.remove(virtExtEsId);
				dmxVirtualEses.mapVirtEsIdToCfgObj.remove(tmpIdEs);
				dmxVirtualEses.mapVirtEsIdToEsei.remove(tmpIdEs);
			}
			//
			dmxVirtualEses.mapExternalIsIdToInternalVirtIsId.remove(extRealIsId);
			dmxVirtualEses.mapVirtIsIdToCfgObj.remove(virtIsId);
		}

		// create new virtual ESs for new/modified streams
		Set<@NonNull String> addedAndModifiedStreamIds = new HashSet<>();
		addedAndModifiedStreamIds.addAll(mappingsIs.deltaAddedIds);
		addedAndModifiedStreamIds.addAll(mappingsIs.deltaModifiedIds);
		for (String extStreamId : addedAndModifiedStreamIds) {
			RtspSrvConfigStreamsStream streamCfgObj = mappingsIs.mapExternalIdToCfgObjCur.get(extStreamId);
			if (streamCfgObj == null || ! streamCfgObj.getEnabled()) {
				continue;
			}
			Set<@NonNull String> tmpSsIds = streamCfgObj.getSubStreamIds();
			if (tmpSsIds.size() != 1 || ! mappingsEs.mapExternalIdToCfgObjCur.containsKey(tmpSsIds.iterator().next())) {
				continue;
			}
			final String extRealSsId = tmpSsIds.iterator().next();
			RtspSrvConfigStreamsSs realSsCfgObj = mappingsEs.mapExternalIdToCfgObjCur.get(extRealSsId);
			if (! realSsCfgObj.getEnabled()) {
				continue;
			}
			boolean tmpResB;
			if (realSsCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_FC ||
					realSsCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_RTSP ||
					realSsCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_AF) {
				tmpResB = createVirtualEsesForOneStream_dmx(extStreamId, streamCfgObj, realSsCfgObj, extRealSsId);
			} else {
				tmpResB = true;
			}
			if (! tmpResB) {
				return false;
			}
		}
		return true;
	}

	private boolean createVirtualEsesForOneStream_dmx(
				@NonNull String extStreamId,
				@NonNull RtspSrvConfigStreamsStream realStreamCfgObj,
				@NonNull RtspSrvConfigStreamsSs realSsCfgObj,
				@NonNull String extRealSsId
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".createVirtualEsesForOneStream_dmx()";

		try {
			//logDebug(FNC_NAME, "Creating virtual ES for muxed IS : " + extStreamId);
			StreamsCfgVirtualEsMapper.VirtualEsObjs veo;
			if (realSsCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_FC ||
					realSsCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_RTSP) {
				veo = StreamsCfgVirtualEsMapper.createVirtualEsesFromDemuxedSource_fcOrRtsp(
						realSsCfgObj,
						extRealSsId
					);
			} else {
				veo = StreamsCfgVirtualEsMapper.createVirtualEsesFromDemuxedSource_af(
						realSsCfgObj,
						extRealSsId
					);
			}
			dmxVirtualEses.mapVirtExternalEsIdToInternal.putAll(veo.mapVirtExternalEsIdToInternal());
			dmxVirtualEses.mapVirtEsIdToCfgObj.putAll(veo.mapVirtInternalIdEsToEsCfgObj());
			dmxVirtualEses.mapVirtEsIdToEsei.putAll(veo.mapVirtEsIdToEsei());
			//
			RtspSrvConfigStreamsStream virtStreamCfg = RtspSrvConfigStreamsStream.createVirtual(
					realStreamCfgObj,
					veo.mapVirtExternalEsIdToInternal().keySet()
				);
			RtspProtoIdInputSource virtIsId = StreamsCfgIdMapperHelper.computeInternalIsId(extStreamId);
			dmxVirtualEses.mapExternalIsIdToInternalVirtIsId.put(extStreamId, virtIsId);
			dmxVirtualEses.mapVirtIsIdToCfgObj.put(virtIsId, virtStreamCfg);
		} catch (ConfigInvalidException e) {
			logError(FNC_NAME, "Failed to create virtual ES: " + e.getMessage());
			return false;
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void updateRawFileOrMqMetaInfo(boolean isRawFile) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateRawFileOrMqMetaInfo()";

		RawFileOrMqEsMetaInfo outputEsMetaInfo = (isRawFile ? rawFileEsMetaInfo : mqEsMetaInfo);

		Set<@NonNull String> addedAndModifiedSsIds = new HashSet<>();
		addedAndModifiedSsIds.addAll(mappingsEs.deltaAddedIds);
		addedAndModifiedSsIds.addAll(mappingsEs.deltaModifiedIds);

		//
		for (String extSubStreamId : mappingsEs.deltaDeletedIds) {
			RtspProtoIdEsSource intEsId = mappingsEs.mapExternalToInternalIdPrev.get(extSubStreamId);
			if (intEsId == null) {
				continue;
			}
			outputEsMetaInfo.mapEsIdToEsei.remove(intEsId);
		}
		for (String extSubStreamId : addedAndModifiedSsIds) {
			RtspProtoIdEsSource intEsId = mappingsEs.mapExternalToInternalIdCur.get(extSubStreamId);
			if (intEsId == null) {
				continue;
			}
			outputEsMetaInfo.mapEsIdToEsei.remove(intEsId);
		}

		//
		for (String extSubStreamId : addedAndModifiedSsIds) {
			RtspSrvConfigStreamsSs subStreamCfgObj = mappingsEs.mapExternalIdToCfgObjCur.get(extSubStreamId);
			if (subStreamCfgObj == null || ! subStreamCfgObj.getEnabled() ||
					(
							(isRawFile && subStreamCfgObj.getEsSourceType() != RtspProtoEsSourceType.ST_ES_RAW_FILE) ||
							(! isRawFile && subStreamCfgObj.getEsSourceType() != RtspProtoEsSourceType.ST_ES_MQ))
						) {
				continue;
			}
			RtspProtoIdEsSource intEsId = mappingsEs.mapExternalToInternalIdCur.get(extSubStreamId);
			if (intEsId == null) {
				continue;
			}
			try {
				RtspProtoEsSourceExpandedInfo tmpMeta;
				if (isRawFile) {
					//logDebug(FNC_NAME, "Updating meta info for ES Raw File: " + extSubStreamId);
					tmpMeta = StreamsCfgReadEsRawFileMeta.readMetaInfoOfEsRawFile(
							intEsId,
							subStreamCfgObj.getSsSourceEsRawFile().orElseThrow()
						);
				} else {
					/*
					 * Create a preliminary RtspProtoEsSourceExpandedInfo object for the MQ Elementary Sub-Stream Source.
					 * Later a dedicated thread for the MQ will update and replace the RtspProtoEsSourceExpandedInfo object.
					 */
					//logDebug(FNC_NAME, "Updating meta info for ES MQ: " + extSubStreamId);
					RtspProtoClientCredentials tmpCred = RtspProtoClientCredentials.of(
							subStreamCfgObj.getSsSourceEsMq().orElseThrow().getUsername(),
							subStreamCfgObj.getSsSourceEsMq().orElseThrow().getPassword()
						);
					ProUri tmpUri = subStreamCfgObj.getSsSourceEsMq().orElseThrow().getInputUri();
					tmpMeta = createEsei_mqDummy(tmpCred, tmpUri);
				}
				outputEsMetaInfo.mapEsIdToEsei.put(intEsId, tmpMeta);
			} catch (ConfigInvalidException e) {
				logWarn(FNC_NAME, "ConfigInvalidException caught: " + e.getMessage());
			}
		}
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo createEsei_mqDummy(
				@NonNull RtspProtoClientCredentials credentials,
				@NonNull ProUri inputUri
			) {
		return new RtspProtoEsSourceExpandedInfo(
				-1,
				RtpPacketType.UNKNOWN,
				RtspProtoEsSourceType.ST_ES_MQ,
				inputUri.clone(),
				credentials,
				-1,
				(byte)0,
				SampleRateEnum.UNKNOWN,
				-1,
				false,
				ExtradataContainerHex.ofEmpty(),
				FrameRateEnum.UNKNOWN,
				ExtradataContainerSdp.ofEmpty(),
				null
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void populateAsSvcInputData(@NonNull RtspAsSvcInputData asSvcInputData) {
		asSvcInputData.clear();

		populate_addMappedExternalIsIdsToSet(true, mappingsIs.deltaDeletedIds, asSvcInputData.isIdsDeleted);
		populate_addMappedExternalIsIdsToSet(false, mappingsIs.deltaAddedIds, asSvcInputData.isIdsAdded);
		populate_addMappedExternalIsIdsToSet(false, mappingsIs.deltaModifiedIds, asSvcInputData.isIdsModified);

		populate_addClonedIsCfgsToMap_realAndVirtual(mappingsIs.deltaAddedIds, asSvcInputData.mapIsIdToIsObj);
		populate_addClonedIsCfgsToMap_realAndVirtual(mappingsIs.deltaModifiedIds, asSvcInputData.mapIsIdToIsObj);

		populate_addClonedEsCfgsToMap_onlyReal(mappingsEs.deltaAddedIds, asSvcInputData.mapEsIdToEsObj);
		populate_addClonedEsCfgsToMap_onlyReal(mappingsEs.deltaModifiedIds, asSvcInputData.mapEsIdToEsObj);
		populate_addClonedEsCfgsToMap_onlyVirtual(dmxVirtualEses.mapVirtEsIdToCfgObj.keySet(), asSvcInputData.mapEsIdToEsObj);

		populate_addClonedEseis(dmxVirtualEses.mapVirtEsIdToEsei, asSvcInputData.mapEsIdToEseiObj);
		populate_addClonedEseis(rawFileEsMetaInfo.mapEsIdToEsei, asSvcInputData.mapEsIdToEseiObj);
		populate_addClonedEseis(mqEsMetaInfo.mapEsIdToEsei, asSvcInputData.mapEsIdToEseiObj);
	}

	private void populate_addMappedExternalIsIdsToSet(
				boolean isDeleted,
				@NonNull Set<@NonNull String> inputExternalIds,
				@NonNull Set<@NonNull RtspProtoIdInputSource> outputInternalIds
			) {
		final Map<@NonNull String, @NonNull RtspProtoIdInputSource> map = (isDeleted ?
				mappingsIs.mapExternalToInternalIdPrev : mappingsIs.mapExternalToInternalIdCur
			);
		for (String inputId : inputExternalIds) {
			RtspProtoIdInputSource mappedId = map.get(inputId);
			if (mappedId == null) {
				continue;
			}
			outputInternalIds.add(
					RtspProtoIdInputSource.of(mappedId.getIdStr().orElseThrow())
				);
		}
	}

	private void populate_addClonedIsCfgsToMap_realAndVirtual(
				@NonNull Set<@NonNull String> inputExternalIds,
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> outputObjs
			) {
		for (String inputId : inputExternalIds) {
			boolean isVirt = false;
			RtspProtoIdInputSource mappedId = null;
			if (dmxVirtualEses.mapExternalIsIdToInternalVirtIsId.containsKey(inputId)) {
				mappedId = dmxVirtualEses.mapExternalIsIdToInternalVirtIsId.get(inputId);
				isVirt = (mappedId != null);
			}
			if (! isVirt) {
				mappedId = mappingsIs.mapExternalToInternalIdCur.get(inputId);
			}
			if (mappedId == null) {
				continue;
			}
			RtspSrvConfigStreamsStream tmpInputCfg;
			if (isVirt) {
				tmpInputCfg = dmxVirtualEses.mapVirtIsIdToCfgObj.get(mappedId);
			} else {
				tmpInputCfg = mappingsIs.mapExternalIdToCfgObjCur.get(inputId);
			}
			if (tmpInputCfg == null) {
				continue;
			}
			outputObjs.put(
					RtspProtoIdInputSource.of(mappedId.getIdStr().orElseThrow()),
					populate_convertStreamCfg(mappedId, tmpInputCfg)
				);
		}
	}

	private static @NonNull RtspProtoInputSource populate_convertStreamCfg(
				@NonNull RtspProtoIdInputSource internalId,
				@NonNull RtspSrvConfigStreamsStream cfgObj
			) {
		RtspProtoInputSource protoInputSource = new RtspProtoInputSource();
		protoInputSource.setIdInputSource(internalId);
		protoInputSource.setEnabled(cfgObj.getEnabled());
		protoInputSource.setNeedsAuthentication(cfgObj.getNeedsAuthentication());
		protoInputSource.setAllowedUserAccountGroups(cfgObj.getAllowedUserAccountGroups());
		protoInputSource.setNeedsEncryption(cfgObj.getNeedsEncryption());
		for (String externalSsId : cfgObj.getSubStreamIds()) {
			RtspProtoIdEsSource internalSsId = StreamsCfgIdMapperHelper.computeInternalEsId(externalSsId);
			protoInputSource.putIdEsSource(internalSsId);
		}
		return protoInputSource;
	}

	private void populate_addClonedEsCfgsToMap_onlyReal(
				@NonNull Set<@NonNull String> inputExternalIds,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> outputObjs
			) {
		for (String inputId : inputExternalIds) {
			RtspProtoIdEsSource mappedId = mappingsEs.mapExternalToInternalIdCur.get(inputId);
			if (mappedId == null) {
				continue;
			}
			RtspSrvConfigStreamsSs tmpInputCfg = mappingsEs.mapExternalIdToCfgObjCur.get(inputId);
			if (tmpInputCfg == null) {
				continue;
			}
			outputObjs.put(
					RtspProtoIdEsSource.of(mappedId.getIdStr().orElseThrow()),
					populate_convertSubStreamCfg(inputId, mappedId, tmpInputCfg)
				);
		}
	}

	private void populate_addClonedEsCfgsToMap_onlyVirtual(
				@NonNull Set<@NonNull RtspProtoIdEsSource> inputInternalIds,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> outputObjs
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".populate_addClonedEsCfgsToMap_onlyVirtual()";

		for (RtspProtoIdEsSource inputId : inputInternalIds) {
			RtspSrvConfigStreamsSs tmpInputCfg = dmxVirtualEses.mapVirtEsIdToCfgObj.get(inputId);
			if (tmpInputCfg == null) {
				continue;
			}
			String tmpExtEsId = null;
			for (Map.Entry<@NonNull String, @NonNull RtspProtoIdEsSource> entry : dmxVirtualEses.mapVirtExternalEsIdToInternal.entrySet()) {
				if (entry.getValue().equals(inputId)) {
					tmpExtEsId = entry.getKey();
					break;
				}
			}
			if (tmpExtEsId == null) {
				logWarn(FNC_NAME, "No external ID found for internal ID: " + inputId);
				continue;
			}
			outputObjs.put(
					RtspProtoIdEsSource.of(inputId.getIdStr().orElseThrow()),
					populate_convertSubStreamCfg(tmpExtEsId, inputId, tmpInputCfg)
				);
		}
	}

	private static @NonNull RtspProtoElementaryStreamSource populate_convertSubStreamCfg(
				@NonNull String externalId,
				@NonNull RtspProtoIdEsSource internalId,
				@NonNull RtspSrvConfigStreamsSs cfgObj
			) {
		RtspProtoElementaryStreamSource protoEsSource = new RtspProtoElementaryStreamSource();
		protoEsSource.setExternalId(externalId);
		protoEsSource.setIdEsSource(internalId);
		protoEsSource.setEnabled(cfgObj.getEnabled());
		return protoEsSource;
	}

	private void populate_addClonedEseis(
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> inputEseis,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> outputEseis
			) {
		for (Map.Entry<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> entry : inputEseis.entrySet()) {
			RtspProtoIdEsSource clonedId = RtspProtoIdEsSource.of(entry.getKey().getIdStr().orElseThrow());
			RtspProtoEsSourceExpandedInfo clonedEsei = entry.getValue().clone();
			outputEseis.put(clonedId, clonedEsei);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}

	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
