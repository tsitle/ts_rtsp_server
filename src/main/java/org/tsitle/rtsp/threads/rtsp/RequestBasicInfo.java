package org.tsitle.rtsp.threads.rtsp;

public class RequestBasicInfo {

	public static class RequestUrlInputOrStreamSource {
		String inputSourceId = null;
		int streamSourceId = -1;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public ServerMessageType serverMessageType = ServerMessageType.UNKNOWN;
	public ServerResponseStatusCode statusCode = ServerResponseStatusCode.OK;
	public RequestUrlInputOrStreamSource requestUrlInputOrStreamSource = null;

	private RequestBasicInfo() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isValid() { return (serverMessageType != ServerMessageType.UNKNOWN && statusCode == ServerResponseStatusCode.OK); }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RequestBasicInfo createUnknown() {
		RequestBasicInfo res = new RequestBasicInfo();
		res.serverMessageType = ServerMessageType.UNKNOWN;
		return res;
	}

	public static RequestBasicInfo createKnownWithError(ServerMessageType serverMessageType, ServerResponseStatusCode statusCode) {
		RequestBasicInfo res = new RequestBasicInfo();
		res.serverMessageType = serverMessageType;
		res.statusCode = statusCode;
		return res;
	}

	public static RequestBasicInfo createOk(ServerMessageType serverMessageType, RequestUrlInputOrStreamSource requestUrlInputOrStreamSource) {
		RequestBasicInfo res = new RequestBasicInfo();
		res.serverMessageType = serverMessageType;
		res.requestUrlInputOrStreamSource = requestUrlInputOrStreamSource;
		return res;
	}

}
