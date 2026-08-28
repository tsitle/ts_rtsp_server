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

final class RtspAsDeltaEsHelper {

	private RtspAsDeltaEsHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static void findMqEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMapStaged,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapCurrent,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapStaged,
				@NonNull Set<@NonNull RtspProtoIdEsSource> dmxEsIds
			) {
		findXxxEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				Set.of(RtspProtoEsSourceType.ST_ES_MQ),
				inputSourceMapStaged,
				eseiMapCurrent,
				eseiMapStaged,
				dmxEsIds
			);
	}

	static void findDmxFcOrRtspEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMapStaged,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapCurrent,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapStaged,
				@NonNull Set<@NonNull RtspProtoIdEsSource> dmxEsIds
			) {
		findXxxEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				Set.of(RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_FC, RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_RTSP),
				inputSourceMapStaged,
				eseiMapCurrent,
				eseiMapStaged,
				dmxEsIds
			);
	}

	static void findDmxJbEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMapStaged,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapCurrent,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapStaged,
				@NonNull Set<@NonNull RtspProtoIdEsSource> dmxEsIds
			) {
		findXxxEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				Set.of(RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_JB),
				inputSourceMapStaged,
				eseiMapCurrent,
				eseiMapStaged,
				dmxEsIds
			);
	}

	static void findMqEsThatAreInUse(
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMap,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMap,
				@NonNull Set<@NonNull RtspProtoIdEsSource> mqEsIdsInUse
			) {
		findXxxEsThatAreInUse(
				Set.of(RtspProtoEsSourceType.ST_ES_MQ),
				inputSourceMap,
				eseiMap,
				mqEsIdsInUse
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void findXxxEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				@NonNull Set<@NonNull RtspProtoEsSourceType> searchForEsSourceTypes,
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMapStaged,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapCurrent,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMapStaged,
				@NonNull Set<@NonNull RtspProtoIdEsSource> outputEsIds
			) {
		outputEsIds.clear();

		//
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> eseiEntryCur : eseiMapCurrent.entrySet()) {
			if (! searchForEsSourceTypes.contains(eseiEntryCur.getValue().esSourceType())) {
				continue;
			}
			if (! eseiMapStaged.containsKey(eseiEntryCur.getKey())) {
				// --> deleted
				outputEsIds.add(eseiEntryCur.getKey().clone());
			}
		}

		//
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> eseiEntryStaged : eseiMapStaged.entrySet()) {
			if (! searchForEsSourceTypes.contains(eseiEntryStaged.getValue().esSourceType())) {
				continue;
			}
			if (! eseiMapCurrent.containsKey(eseiEntryStaged.getKey())) {
				// --> added
				continue;
			}
			if (hasXxxEsChanged(eseiEntryStaged.getValue(), eseiMapCurrent.get(eseiEntryStaged.getKey()))) {
				// --> modified
				outputEsIds.add(eseiEntryStaged.getKey().clone());
			}
		}

		//
		Set<@NonNull RtspProtoIdEsSource> xxxEsIdsInUse = new HashSet<>();
		findXxxEsThatAreInUse(
				searchForEsSourceTypes,
				inputSourceMapStaged,
				eseiMapStaged,
				xxxEsIdsInUse
			);
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> eseiEntryStaged : eseiMapStaged.entrySet()) {
			if (! searchForEsSourceTypes.contains(eseiEntryStaged.getValue().esSourceType())) {
				continue;
			}
			if (! xxxEsIdsInUse.contains(eseiEntryStaged.getKey())) {
				// --> not in use
				outputEsIds.add(eseiEntryStaged.getKey().clone());
			}
		}
	}

	private static void findXxxEsThatAreInUse(
				@NonNull Set<@NonNull RtspProtoEsSourceType> searchForEsSourceTypes,
				@NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMap,
				@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMap,
				@NonNull Set<@NonNull RtspProtoIdEsSource> outputEsIdsInUse
			) {
		outputEsIdsInUse.clear();
		for (RtspProtoInputSource tmpIsObj : inputSourceMap.values()) {
			if (! tmpIsObj.getEnabled()) {
				continue;
			}
			for (RtspProtoIdEsSource tmpIdEs : tmpIsObj.getEsSourceIds()) {
				if (eseiMap.containsKey(tmpIdEs) && searchForEsSourceTypes.contains(eseiMap.get(tmpIdEs).esSourceType())) {
					outputEsIdsInUse.add(tmpIdEs);
				}
			}
		}
	}

	private static boolean hasXxxEsChanged(
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryA,
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryB
			) {
		if (eseiEntryA.esSourceType() != eseiEntryB.esSourceType()) {
			return true;
		}
		return switch (eseiEntryA.esSourceType()) {
				case ST_ES_MQ -> hasMqEsChanged(eseiEntryA, eseiEntryB);
				case ST_DMX_VIRTUAL_ES_FC, ST_DMX_VIRTUAL_ES_MQ_FROM_RTSP -> hasDmxFcOrRtspEsChanged(eseiEntryA, eseiEntryB);
				case ST_DMX_VIRTUAL_ES_MQ_FROM_JB -> hasDmxJbEsChanged(eseiEntryA, eseiEntryB);
				default -> throw new IllegalArgumentException(
						RtspAsDeltaEsHelper.class.getSimpleName() + ".hasXxxEsChanged(): " +
						"invalid ES Source Type: " + eseiEntryA.esSourceType());
			};
	}

	private static boolean hasMqEsChanged(
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryA,
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryB
			) {
		return (! (
				eseiEntryA.credentials().equals(eseiEntryB.credentials()) &&
				eseiEntryA.inputUri().equals(eseiEntryB.inputUri())
			));
	}

	private static boolean hasDmxFcOrRtspEsChanged(
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryA,
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryB
			) {
		return (! (
				eseiEntryA.inputUri().equals(eseiEntryB.inputUri())
			));
	}

	private static boolean hasDmxJbEsChanged(
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryA,
				@NonNull RtspProtoEsSourceExpandedInfo eseiEntryB
			) {
		if ((eseiEntryA.tcSettingsAudio() == null && eseiEntryB.tcSettingsAudio() != null) ||
				(eseiEntryA.tcSettingsAudio() != null && eseiEntryB.tcSettingsAudio() == null)) {
			return true;
		}
		return (! (
				eseiEntryA.inputUri().equals(eseiEntryB.inputUri()) &&
				(eseiEntryA.tcSettingsAudio() == null ||
						eseiEntryA.tcSettingsAudio().equals(eseiEntryB.tcSettingsAudio()))
			));
	}

}
