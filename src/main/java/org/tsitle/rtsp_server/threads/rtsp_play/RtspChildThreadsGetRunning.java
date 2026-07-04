package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;

import java.util.Collection;

public interface RtspChildThreadsGetRunning {

	@NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValuesOnlyRunning();

}
