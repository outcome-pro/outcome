// All the information contained in this source code file is a property of Outcome Professional Services Limited,
// a company registered in the United Kingdom. Use and distribution of any part of the information 
// contained in this source code file without our prior consent is forbidden. If you have an interest 
// in using any part of this source code in your software, please contact hiCarlosSilva@gmail.com.
package pro.outcome.rest;
import pro.outcome.util.Checker;


public abstract class EndpointException extends RuntimeException {

	private final StatusCode _code;
	private final Object[] _parameters;
	private final JsonObject _content;
	
	protected EndpointException(StatusCode code, JsonObject content, Object ... parameters) {
		Checker.checkNull(code);
		if(code == StatusCodes.OK) {
			throw new IllegalArgumentException("status code OK cannot be used in an exception");
		}
		_code = code;
		_parameters = parameters;
		_content = content;
	}

	protected EndpointException(StatusCode code, Object ... parameters) {
		this(code, null, parameters);
	}

	public final String getMessage() {
		return _code.expand(_parameters);
	}
	
	public final StatusCode getErrorCode() {
		return _code;
	}
	
	public final JsonObject getContent() {
		return _content;
	}

	private static final long serialVersionUID = 1L;
}
