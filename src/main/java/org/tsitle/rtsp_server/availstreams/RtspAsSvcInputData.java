package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoElementaryStreamSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RtspAsSvcInputData {

	public final @NonNull Set<@NonNull RtspProtoIdInputSource> isIdsAdded = new HashSet<>();
	public final @NonNull Set<@NonNull RtspProtoIdInputSource> isIdsModified = new HashSet<>();
	public final @NonNull Set<@NonNull RtspProtoIdInputSource> isIdsDeleted = new HashSet<>();

	public final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> mapIsIdToIsObj = new HashMap<>();
	public final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> mapEsIdToEsObj = new HashMap<>();
	public final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapEsIdToEseiObj = new HashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		isIdsAdded.clear();
		isIdsModified.clear();
		isIdsDeleted.clear();

		mapIsIdToIsObj.clear();
		mapEsIdToEsObj.clear();
		mapEsIdToEseiObj.clear();
	}

}
