package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;

import java.util.Optional;

public interface AsGetFileTagsInterface {

	Optional<String> getFileTagsValue(@NonNull RtspProtoIdInputSource idInputSource);

	Optional<String> getFileTagsHash(@NonNull RtspProtoIdInputSource idInputSource);

}
