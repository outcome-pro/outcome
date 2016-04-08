// All the information contained in this source code file is a property of Intersection Labs Limited,
// a company registered in the United Kingdom. Use and distribution of any part of the information 
// contained in this source code file without our prior consent is forbidden. If you have an interest 
// in using any part of this source code in your software, please contact us on listening@connector.im.
package pro.outcome.data;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import pro.outcome.data.Field.Constraint;
import pro.outcome.util.IllegalUsageException;


public class Config extends Entity<ConfigValue> {

	// TYPE:
	public final Field<String> name;
	public final Field<Object> value;

	public interface Properties {
		public static final String BASE_URL = "base-url";
		public static final String ENV = "env";
		public static final String ALLOWED_ORIGINS = "allowed-origins";
	}
	// TODO remove
	/*
	public class Environments extends Flags {
		public static final Flag Dev = createFlag(Environments.class, "d", "Development");
		public static final Flag Test = createFlag("t", "Test");
		public static final Flag Live = createFlag("l", "Live");
	}
	*/
	
	// INSTANCE:
	private final Map<String,ConfigValue> _cache;
	
	public Config() {
		name = addField(String.class, "name", true, Constraint.MANDATORY, Constraint.UNIQUE, Constraint.READ_ONLY);
		value = addField(Object.class, "value", false, (Object)null, Constraint.MANDATORY);
		_cache = new HashMap<>();
	}
	
	public Field<?>[] getNaturalKeyFields() {
		return new Field<?>[] { name };
	}

	public Object getValue(String name) {
		ConfigValue value = null;
		if(_cache.containsKey(name)) {
			value = _cache.get(name);
		}
		else {
			value = findSingle(this.name.toArg(name));
			_cache.put(name, value);
		}
		if(value == null) {
			return null;
		}
		return value.getValue();
	}
	
	public String getBaseUrl() {
		String baseUrl = (String)getValue(Properties.BASE_URL);
		if(baseUrl == null) {
			throw new IllegalUsageException("base URL property has not been set");
		}
		return baseUrl;
	}

	public String getEnvironment() {
		String e = (String)getValue(Properties.ENV);
		if(e == null) {
			throw new IllegalUsageException("environment config property has not been set");
		}
		return e;
	}
	
	public List<String> getAllowedOrigins() {
		@SuppressWarnings("unchecked")
		List<String> ao = (List<String>)getValue(Properties.ALLOWED_ORIGINS);
		return ao == null ? new ArrayList<String>(0) : ao;
	}
}
