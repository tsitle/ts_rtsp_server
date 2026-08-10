package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.availstreams.RtspAsSvcInputData;
import org.tsitle.rtsp_server.availstreams.RtspAvailableStreamsSvc;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsSsNg;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsStreamNg;
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

	private static class VirtualEses {
		final @NonNull Map<@NonNull String, @NonNull RtspProtoIdInputSource> mapExternalIsIdToInternalVirtIsId = new HashMap<>();

		final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspSrvConfigStreamsStreamNg> mapVirtIsIdToCfgObj = new HashMap<>();
		final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSsNg> mapVirtEsIdToCfgObj = new HashMap<>();

		final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapVirtEsIdToEsei = new HashMap<>();
	}

	private static class RawFileEsMetaInfo {
		final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapEsIdToEsei = new HashMap<>();
	}

	private static final int XX_ID_HASH_LEN = 8;

	private final @NonNull LogMsgInterface logMsgInterface;

	private final Mappings<@NonNull RtspProtoIdInputSource, @NonNull RtspSrvConfigStreamsStreamNg> mappingsIs = new Mappings<>();
	private final Mappings<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSsNg> mappingsEs = new Mappings<>();

	private final @NonNull VirtualEses virtualEses = new VirtualEses();
	private final @NonNull RawFileEsMetaInfo rawFileEsMetaInfo = new RawFileEsMetaInfo();

	StreamsCfgMapper(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	boolean mapStreamsCfg(
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSsNg> currentSubStreams,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStreamNg> currentStreams,
				@NonNull RtspAsSvcInputData asSvcInputData
			) {
		internalMapXxCfg(RtspProtoIdInputSource.class, RtspSrvConfigStreamsStreamNg.class, currentStreams, mappingsIs);
		internalMapXxCfg(RtspProtoIdEsSource.class, RtspSrvConfigStreamsSsNg.class, currentSubStreams, mappingsEs);

		// compare previous vs. current configs
		compareXxCfgs(mappingsIs);
		compareXxCfgs(mappingsEs);
		compareSubStreamsPerStream();

		//
		debugPrintStreams();  // @TODO

		//
		updateVirtualEses();

		//
		updateRawFileMetaInfo();

		// --------------------------------------------------------

		populateAsSvcInputData(asSvcInputData);

		// --------------------------------------------------------

		// move current configs to previous
		mappingsIs.moveCurrentToPrevious();
		mappingsEs.moveCurrentToPrevious();

		//
		return mappingsIs.haveChanges();
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
				RtspProtoIdInputSource tmpIntId = RtspAvailableStreamsSvc.computeInternalIsId(entry.getKey());
				mappings.mapExternalToInternalIdCur.put(entry.getKey(), idType.cast(tmpIntId));
			} else if (idType == RtspProtoIdEsSource.class) {
				RtspProtoIdEsSource tmpIntId = RtspAvailableStreamsSvc.computeInternalEsId(entry.getKey());
				mappings.mapExternalToInternalIdCur.put(entry.getKey(), idType.cast(tmpIntId));
			} else {
				throw new RuntimeException(StreamsCfgMapper.class.getSimpleName() + ": Unsupported ID type: " + idType);
			}
			//
			if (! mappings.mapExternalToInternalIdPrev.containsKey(entry.getKey())) {
				mappings.deltaAddedIds.add(entry.getKey());
			}
			//
			if (cfgType == RtspSrvConfigStreamsStreamNg.class) {
				RtspSrvConfigStreamsStreamNg casted = (RtspSrvConfigStreamsStreamNg)cfgType.cast(entry.getValue());
				mappings.mapExternalIdToCfgObjCur.put(entry.getKey(), cfgType.cast(casted.clone()));
			} else if (cfgType == RtspSrvConfigStreamsSsNg.class) {
				RtspSrvConfigStreamsSsNg casted = (RtspSrvConfigStreamsSsNg)cfgType.cast(entry.getValue());
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
		for (Map.Entry<String, RtspSrvConfigStreamsStreamNg> entryStream : mappingsIs.mapExternalIdToCfgObjCur.entrySet()) {
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

	private void debugPrintStreams() {
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
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void updateVirtualEses() {
		// delete virtual ESs for deleted/new/modified streams
		Set<@NonNull String> addedAndModAndDelStreamIds = new HashSet<>();
		addedAndModAndDelStreamIds.addAll(mappingsIs.deltaAddedIds);
		addedAndModAndDelStreamIds.addAll(mappingsIs.deltaModifiedIds);
		addedAndModAndDelStreamIds.addAll(mappingsIs.deltaDeletedIds);
		for (String extRealIsId : addedAndModAndDelStreamIds) {
			RtspProtoIdInputSource virtIsId = virtualEses.mapExternalIsIdToInternalVirtIsId.get(extRealIsId);
			if (virtIsId == null) {
				continue;
			}
			RtspSrvConfigStreamsStreamNg virtIsObj = virtualEses.mapVirtIsIdToCfgObj.get(virtIsId);
			if (virtIsObj == null) {
				continue;
			}
			for (String virtExtEsId : virtIsObj.getSubStreamIds()) {
				RtspProtoIdEsSource tmpIdEs = RtspAvailableStreamsSvc.computeInternalEsId(virtExtEsId);
				virtualEses.mapVirtEsIdToCfgObj.remove(tmpIdEs);
				virtualEses.mapVirtEsIdToEsei.remove(tmpIdEs);
			}
			//
			virtualEses.mapExternalIsIdToInternalVirtIsId.remove(extRealIsId);
			virtualEses.mapVirtIsIdToCfgObj.remove(virtIsId);
		}

		// create new virtual ESs for new/modified streams
		Set<@NonNull String> addedAndModifiedStreamIds = new HashSet<>();
		addedAndModifiedStreamIds.addAll(mappingsIs.deltaAddedIds);
		addedAndModifiedStreamIds.addAll(mappingsIs.deltaModifiedIds);
		for (String extStreamId : addedAndModifiedStreamIds) {
			RtspSrvConfigStreamsStreamNg streamCfgObj = mappingsIs.mapExternalIdToCfgObjCur.get(extStreamId);
			if (streamCfgObj == null || ! streamCfgObj.getEnabled()) {
				continue;
			}
			Set<@NonNull String> tmpSsIds = streamCfgObj.getSubStreamIds();
			if (tmpSsIds.size() != 1 || ! mappingsEs.mapExternalIdToCfgObjCur.containsKey(tmpSsIds.iterator().next())) {
				continue;
			}
			final String extRealSsId = tmpSsIds.iterator().next();
			RtspSrvConfigStreamsSsNg realSsCfgObj = mappingsEs.mapExternalIdToCfgObjCur.get(extRealSsId);
			if (! realSsCfgObj.getEnabled() || (
					realSsCfgObj.getEsSourceType() != RtspProtoEsSourceType.ST_DEMUX_MS_FILE &&
							realSsCfgObj.getEsSourceType() != RtspProtoEsSourceType.ST_DEMUX_MS_RTSP)) {
				continue;
			}
			createVirtualEsesForOneStream(extStreamId, streamCfgObj, realSsCfgObj, extRealSsId);
		}
	}

	private void createVirtualEsesForOneStream(
				@NonNull String extStreamId,
				@NonNull RtspSrvConfigStreamsStreamNg realStreamCfgObj,
				@NonNull RtspSrvConfigStreamsSsNg realSsCfgObj,
				@NonNull String extRealSsId
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".createVirtualEsesForOneStream()";

		try {
			logDebug(FNC_NAME, "Creating virtual ES for muxed IS : " + extStreamId);  // @TODO
			StreamsCfgVirtualEsMapper.VirtualEsObjs veo = StreamsCfgVirtualEsMapper.createVirtualEsesFromDemuxedSource(
					realSsCfgObj,
					extRealSsId
				);
			virtualEses.mapVirtEsIdToCfgObj.putAll(veo.mapVirtInternalIdEsToEsCfgObj());
			virtualEses.mapVirtEsIdToEsei.putAll(veo.mapVirtEsIdToEsei());
			//
			RtspSrvConfigStreamsStreamNg virtStreamCfg = RtspSrvConfigStreamsStreamNg.createVirtual(
					realStreamCfgObj,
					veo.mapVirtExternalEsIdToInternal().keySet()
				);
			RtspProtoIdInputSource virtIsId = RtspAvailableStreamsSvc.computeInternalIsId(extStreamId);
			virtualEses.mapExternalIsIdToInternalVirtIsId.put(extStreamId, virtIsId);
			virtualEses.mapVirtIsIdToCfgObj.put(virtIsId, virtStreamCfg);
		} catch (ConfigInvalidException e) {
			logWarn(FNC_NAME, "Failed to create virtual ES: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void updateRawFileMetaInfo() {
		final String FNC_NAME = getClass().getSimpleName() + ".updateRawFileMetaInfo()";

		Set<@NonNull String> addedAndModifiedSsIds = new HashSet<>();
		addedAndModifiedSsIds.addAll(mappingsEs.deltaAddedIds);
		addedAndModifiedSsIds.addAll(mappingsEs.deltaModifiedIds);

		//
		for (String extSubStreamId : mappingsEs.deltaDeletedIds) {
			RtspProtoIdEsSource intEsId = mappingsEs.mapExternalToInternalIdPrev.get(extSubStreamId);
			if (intEsId == null) {
				continue;
			}
			rawFileEsMetaInfo.mapEsIdToEsei.remove(intEsId);
		}
		for (String extSubStreamId : addedAndModifiedSsIds) {
			RtspProtoIdEsSource intEsId = mappingsEs.mapExternalToInternalIdCur.get(extSubStreamId);
			if (intEsId == null) {
				continue;
			}
			rawFileEsMetaInfo.mapEsIdToEsei.remove(intEsId);
		}

		//
		for (String extSubStreamId : addedAndModifiedSsIds) {
			RtspSrvConfigStreamsSsNg subStreamCfgObj = mappingsEs.mapExternalIdToCfgObjCur.get(extSubStreamId);
			if (subStreamCfgObj == null || ! subStreamCfgObj.getEnabled() ||
					subStreamCfgObj.getEsSourceType() != RtspProtoEsSourceType.ST_ES_RAW_FILE) {
				continue;
			}
			RtspProtoIdEsSource intEsId = mappingsEs.mapExternalToInternalIdCur.get(extSubStreamId);
			if (intEsId == null) {
				continue;
			}
			try {
				logDebug(FNC_NAME, "Updating meta info for ES raw file: " + extSubStreamId);  // @TODO
				RtspProtoEsSourceExpandedInfo tmpMeta = StreamsCfgReadEsRawFileMeta.readMetaInfoOfEsRawFile(
						intEsId,
						subStreamCfgObj.getSsSourceEsRawFile().orElseThrow()
					);
				rawFileEsMetaInfo.mapEsIdToEsei.put(intEsId, tmpMeta);
			} catch (ConfigInvalidException e) {
				logWarn(FNC_NAME, "ConfigInvalidException caught: " + e.getMessage());
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void populateAsSvcInputData(@NonNull RtspAsSvcInputData asSvcInputData) {
		asSvcInputData.clear();

		populate_addMappedExternalIsIdsToSet(true, mappingsIs.deltaDeletedIds, asSvcInputData.isIdsDeleted);
		populate_addMappedExternalIsIdsToSet(false, mappingsIs.deltaAddedIds, asSvcInputData.isIdsAdded);
		populate_addMappedExternalIsIdsToSet(false, mappingsIs.deltaModifiedIds, asSvcInputData.isIdsModified);

		populate_addClonedIsCfgsToMap_realAndVirtual(mappingsIs.deltaAddedIds, asSvcInputData.mapIsIdToCfgObj);
		populate_addClonedIsCfgsToMap_realAndVirtual(mappingsIs.deltaModifiedIds, asSvcInputData.mapIsIdToCfgObj);

		populate_addClonedEsCfgsToMap_onlyReal(mappingsEs.deltaAddedIds, asSvcInputData.mapEsIdToCfgObj);
		populate_addClonedEsCfgsToMap_onlyReal(mappingsEs.deltaModifiedIds, asSvcInputData.mapEsIdToCfgObj);
		populate_addClonedEsCfgsToMap_onlyVirtual(virtualEses.mapVirtEsIdToCfgObj.keySet(), asSvcInputData.mapEsIdToCfgObj);

		populate_addClonedEseis(virtualEses.mapVirtEsIdToEsei, asSvcInputData.mapEsIdToEsei);
		populate_addClonedEseis(rawFileEsMetaInfo.mapEsIdToEsei, asSvcInputData.mapEsIdToEsei);
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
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspSrvConfigStreamsStreamNg> outputCfgs
			) {
		for (String inputId : inputExternalIds) {
			boolean isVirt = false;
			RtspProtoIdInputSource mappedId = null;
			if (virtualEses.mapExternalIsIdToInternalVirtIsId.containsKey(inputId)) {
				mappedId = virtualEses.mapExternalIsIdToInternalVirtIsId.get(inputId);
				isVirt = (mappedId != null);
			}
			if (! isVirt) {
				mappedId = mappingsIs.mapExternalToInternalIdCur.get(inputId);
			}
			if (mappedId == null) {
				continue;
			}
			RtspSrvConfigStreamsStreamNg tmpInputCfg;
			if (isVirt) {
				tmpInputCfg = virtualEses.mapVirtIsIdToCfgObj.get(mappedId);
			} else {
				tmpInputCfg = mappingsIs.mapExternalIdToCfgObjCur.get(inputId);
			}
			if (tmpInputCfg == null) {
				continue;
			}
			outputCfgs.put(mappedId, tmpInputCfg.clone());
		}
	}

	private void populate_addClonedEsCfgsToMap_onlyReal(
				@NonNull Set<@NonNull String> inputExternalIds,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSsNg> outputCfgs
			) {
		for (String inputId : inputExternalIds) {
			RtspProtoIdEsSource mappedId = mappingsEs.mapExternalToInternalIdCur.get(inputId);
			if (mappedId == null) {
				continue;
			}
			RtspSrvConfigStreamsSsNg tmpInputCfg = mappingsEs.mapExternalIdToCfgObjCur.get(inputId);
			if (tmpInputCfg == null) {
				continue;
			}
			outputCfgs.put(mappedId, tmpInputCfg.clone());
		}
	}

	private void populate_addClonedEsCfgsToMap_onlyVirtual(
				@NonNull Set<@NonNull RtspProtoIdEsSource> inputInternalIds,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSsNg> outputCfgs
			) {
		for (RtspProtoIdEsSource inputId : inputInternalIds) {
			RtspSrvConfigStreamsSsNg tmpInputCfg = virtualEses.mapVirtEsIdToCfgObj.get(inputId);
			if (tmpInputCfg == null) {
				continue;
			}
			outputCfgs.put(inputId, tmpInputCfg.clone());
		}
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

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}

	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
