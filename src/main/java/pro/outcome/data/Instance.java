// All the information contained in this source code file is a property of Outcome Professional Services Limited,
// a company registered in the United Kingdom. Use and distribution of any part of the information 
// contained in this source code file without our prior consent is forbidden. If you have an interest 
// in using any part of this source code in your software, please contact hiCarlosSilva@gmail.com.
package pro.outcome.data;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import pro.outcome.util.Checker;
import pro.outcome.util.ConstructorNotFoundException;
import pro.outcome.util.ConstructorNotVisibleException;
import pro.outcome.util.IntegrityException;
import pro.outcome.util.Reflection;
import pro.outcome.util.Strings;
import com.google.appengine.api.datastore.Entity;


public abstract class Instance<M extends Model> {

	// TYPE:
	static <I extends Instance<?>> I newFrom(Class<I> c, Entity e) {
		try {
			I i = Reflection.createObject(c);
			i.setGoogleEntity(e);
			return i;
		}
		catch(ConstructorNotFoundException cnfe) {
			throw new IntegrityException(c.getSimpleName()+" needs to have an empty constructor");
		}
		catch(ConstructorNotVisibleException cnfe) {
			throw new IntegrityException(c.getSimpleName()+"'s empty constructor must be visible");
		}
	}
	
	// INSTANCE:
	private Entity _e;
	private final Map<Field<?>,Object> _updates;
	
	protected Instance() {
		_e = new Entity(getModel().getEntityName());
		_updates = new HashMap<>();
	}

	protected Instance(Long id) {
		Checker.checkNull(id);
		Checker.checkMinValue(id, 1);
		_e = new Entity(getModel().getEntityName(), id);
		_updates = new HashMap<>();
	}

	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o == null) {
			return false;
		}
		if(!(o instanceof Instance)) {
			return false;
		}
		return _e.equals(((Instance<?>)o)._e);
	}
	
	public int hashCode() {
		return _e.hashCode();
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder(getModel().getInstanceName());
		sb.append(':');
		sb.append(' ');
		Iterator<Field<?>> it = getModel().getFields().values().iterator(); 
		while(it.hasNext()) {
			Field<?> f = it.next();
			sb.append('[');
			sb.append(f.getName());
			sb.append('=');
			sb.append(getValue(f));
			sb.append(']');
			if(it.hasNext()) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	public abstract M getModel();

	public final Long getId() { return _e.getKey().getId(); }
	public final Date getTimeCreated() { return getValue(getModel().timeCreated); }
	public final Date getTimeUpdated() { return getValue(getModel().timeUpdated); }

	@SuppressWarnings("unchecked")
	protected <T> T getValue(Field<T> f) {
		Checker.checkNull(f);
		_checkField(f);
		// Primary key (not stored as a property):
		if(f == getModel().id) {
			return (T)getId();
		}
		// Check if we have the object cached:
		T value = (T)_updates.get(f);
		if(value != null) {
			return value;
		}
		// We don't, extract from the Google entity and convert:
		return f.toObject(_e.getProperty(f.getName()));
	}

	protected <T> void setValue(Field<T> f, T value) {
		Checker.checkNull(f);
		_checkField(f);
		// Primary key:
		if(f == getModel().id) {
			throw new IllegalArgumentException("cannot set primary key");
		}
		// Validate data type (it should be enforced by the compiler):
		if(value != null) {
			if(!f.getType().isAssignableFrom(value.getClass())) {
				throw new IntegrityException(value.getClass());
			}
		}
		// TODO Validate that data type is accepted by GAE: (note this will be done on Field, do we really need it here?		
		// Validate format:
		// TODO
		// if(f.getValidator() != null) {
		// Validate mandatory constraint:
		if(value == null && f.isMandatory()) {
			throw new MandatoryConstraintException(f);
		}
		// Validate read-only constraint:
		if(_willUpdate()) {
			if(f.isReadOnly()) {
				throw new ReadOnlyConstraintException(f, value);
			}
		}
		// Validate auto-gen constraint:
		if(f.isAutoGenerated()) {
			throw new AutoGenConstraintException(f, value);
		}
		// Track update:
		_updates.put(f,  value);
		// Check if the field is being set to its current value:
		_removeIfNotUpdated(f, value);
	}

	public boolean hasUpdates() {
		return _updates.size() > 0;
	}
	
	public boolean isPersisted() {
		return _e.getKey().isComplete();
	}
	
	public QueryArg[] getNaturalKeyAsArg() {
		Field<?>[] fields = getModel().getNaturalKeyFields();
		QueryArg[] arg = new QueryArg[fields.length];
		for(int i=0; i<fields.length; i++) {
			arg[i] = new QueryArg(fields[i], getValue(fields[i]));
		}
		return arg;
	}

	// For Facade:
	Entity getGoogleEntity() {
		return _e;
	}
	
	// For Facade:
	Set<Map.Entry<Field<?>,Object>> getUpdates() {
		return new HashMap<Field<?>,Object>(_updates).entrySet();
	}

	// For Facade:
	void updateFrom(Instance<?> i) {
		_updates.clear();
		_updates.putAll(i._updates);
		// Remove any redundant updates:
		for(Field<?> f : i._updates.keySet()) {
			_removeIfNotUpdated(f, _updates.get(f));
		}
	}
	
	// For Facade:
	void flush(Field<?> f, Object value) {
		value = f.toPrimitive(value);
		if(f.isIndexed()) {
			_e.setProperty(f.getName(), value);
		}
		else {
			_e.setUnindexedProperty(f.getName(), value);
		}
		// Its now safe to clear this update:
		_updates.remove(f);
	}

	// For Self.newFrom:
	void setGoogleEntity(Entity e) {
		_updates.clear();
		//_e.setPropertiesFrom(e);
		_e = e;
	}
	
	private void _checkField(Field<?> f) {
		// Check if setting a field that pertains to this entity:
		if(!getModel().getFields().containsValue(f)) {
			throw new IllegalArgumentException(Strings.expand("field {} cannot be used in entity {}", f.getFullName(), getModel().getEntityName()));
		}
	}

	private boolean _willUpdate() {
		return _e.getKey().isComplete();
	}
	
	private void _removeIfNotUpdated(Field<?> f, Object value) {
		Object current = _e.getProperty(f.getName());
		if(value == null) {
			if(current == null) {
				_updates.remove(f);
			}
		}
		else {
			if(value.equals(current)) {
				_updates.remove(f);
			}
		}
	}
}
