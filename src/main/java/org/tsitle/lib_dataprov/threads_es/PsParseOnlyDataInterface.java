package org.tsitle.lib_dataprov.threads_es;

import org.jspecify.annotations.NonNull;
import io.github.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import io.github.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import io.github.tsitle.lib_xrtxp.common.buffers.BufferView;

interface PsParseOnlyDataInterface<I extends CodecInfoInterface<I>> {

	@NonNull I parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException;

}
