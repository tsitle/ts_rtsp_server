package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;

interface PsGetFrameLenFromAvInfoInterface<I extends CodecInfoInterface<I>> {

	int getFrameLen(@NonNull I avInfo);

}
