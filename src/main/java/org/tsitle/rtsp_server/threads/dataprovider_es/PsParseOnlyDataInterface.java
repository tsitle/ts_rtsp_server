package org.tsitle.rtsp_server.threads.dataprovider_es;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

interface PsParseOnlyDataInterface<I extends CodecInfoInterface<I>> {

	@NonNull I parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException;

}
