package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class AvailableStreamsMqEsDelta {

	private AvailableStreamsMqEsDelta() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static void findMqEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMapStaged,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapCurrent,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapStaged,
				@NonNull Set<@NonNull RtspProtoIdEsSource> mqEsIds
			) {
		mqEsIds.clear();

		//
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> eseiEntryCur : eseiMapCurrent.entrySet()) {
			if (eseiEntryCur.getValue().esSourceType() != RtspProtoEsSourceType.ST_ES_MQ) {
				continue;
			}
			if (! eseiMapStaged.containsKey(eseiEntryCur.getKey())) {
				// --> deleted
				mqEsIds.add(eseiEntryCur.getKey().clone());
			}
		}

		//
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> eseiEntryStaged : eseiMapStaged.entrySet()) {
			if (eseiEntryStaged.getValue().esSourceType() != RtspProtoEsSourceType.ST_ES_MQ) {
				continue;
			}
			if (! eseiMapCurrent.containsKey(eseiEntryStaged.getKey())) {
				// --> added
				continue;
			}
			if (hasMqEsChanged(eseiEntryStaged.getValue(), eseiMapCurrent.get(eseiEntryStaged.getKey()))) {
				// --> modified
				mqEsIds.add(eseiEntryStaged.getKey().clone());
			}
		}

		//
		Set<@NonNull RtspProtoIdEsSource> mqEsIdsInUse = new HashSet<>();
		findMqEsThatAreInUse(
				inputSourceMapStaged,
				eseiMapStaged,
				mqEsIdsInUse
			);
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> eseiEntryStaged : eseiMapStaged.entrySet()) {
			if (eseiEntryStaged.getValue().esSourceType() != RtspProtoEsSourceType.ST_ES_MQ) {
				continue;
			}
			if (! mqEsIdsInUse.contains(eseiEntryStaged.getKey())) {
				// --> not in use
				mqEsIds.add(eseiEntryStaged.getKey().clone());
			}
		}
	}

	static void findMqEsThatAreInUse(
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMap,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMap,
				@NonNull Set<@NonNull RtspProtoIdEsSource> mqEsIdsInUse
			) {
		mqEsIdsInUse.clear();
		for (RtspProtoInputSource tmpIsObj : inputSourceMap.values()) {
			if (! tmpIsObj.getEnabled()) {
				continue;
			}
			for (RtspProtoIdEsSource tmpIdEs : tmpIsObj.getEsSourceIds()) {
				if (eseiMap.containsKey(tmpIdEs) &&
						eseiMap.get(tmpIdEs).esSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
					mqEsIdsInUse.add(tmpIdEs);
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static boolean hasMqEsChanged(
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryA,
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryB
			) {
		return (! (
				eseiEntryA.esSourceType().equals(eseiEntryB.esSourceType()) &&
				eseiEntryA.credentials().equals(eseiEntryB.credentials()) &&
				eseiEntryA.inputUri().equals(eseiEntryB.inputUri())
			));
	}

}
