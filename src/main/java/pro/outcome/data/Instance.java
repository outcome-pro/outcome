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
import java.util.HashSet;
import pro.outcome.util.Checker;
import pro.outcome.util.ConstructorNotFoundException;
import pro.outcome.util.ConstructorNotVisibleException;
import pro.outcome.util.IntegrityException;
import pro.outcome.util.Reflection;
import static pro.outcome.util.Shortcuts.*;


public abstract class Instance<E extends Entity<?>> {

	// TYPE:
	static <I extends Instance<?>> I newFrom(Class<I> c, com.google.appengine.api.datastore.Entity e) {
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
	private com.google.appengine.api.datastore.Entity _e;
	private final Map<Property<?>,Object> _updates;
	
	protected Instance() {
		_e = new com.google.appengine.api.datastore.Entity(getEntity().getName());
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
		StringBuilder sb = new StringBuilder(getEntity().getInstanceName());
		sb.append(':');
		sb.append(' ');
		Iterator<Property<?>> it = getEntity().getProperties().values().iterator(); 
		while(it.hasNext()) {
			Property<?> prop = it.next();
			sb.append('[');
			sb.append(prop.getName());
			sb.append('=');
			sb.append(getValue(prop));
			sb.append(']');
			if(it.hasNext()) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	public E getEntity() {
		return (E)Entities.getEntityForInstance(getClass());
	}

	public final Long getId() { return _e.getKey().getId(); }
	public final Date getTimeCreated() { return getValue(getEntity().timeCreated); }
	public final Date getTimeUpdated() { return getValue(getEntity().timeUpdated); }

	@SuppressWarnings("unchecked")
	protected <T> T getValue(Property<T> prop) {
		Checker.checkNull(prop);
		_checkProperty(prop);
		// Primary key (not stored as a property):
		if(prop == getEntity().id) {
			return (T)getId();
		}
		// Check if we have the object cached:
		// (we need to use containsKey to cater for updates to NULL)
		if(_updates.containsKey(prop)) {
			return (T)_updates.get(prop);
		}
		// We don't, extract from the Google entity and convert:
		return prop.toObject(_e.getProperty(prop.getName()));
	}

	protected <T> void setValue(Property<T> prop, T value) {
		Checker.checkNull(prop);
		_checkProperty(prop);
		// Primary key:
		if(prop == getEntity().id) {
			throw new IllegalArgumentException("cannot set primary key");
		}
		// Validate data type (it should be enforced by the compiler):
		if(value != null) {
			if(!prop.getType().isAssignableFrom(value.getClass())) {
				throw new IntegrityException(value.getClass());
			}
		}
		// TODO Validate that data type is accepted by GAE: (note this will be done on Property, do we really need it here?		
		// Validate format:
		// TODO
		// if(f.getValidator() != null) {
		// Validate mandatory constraint:
		if(value == null && prop.isMandatory()) {
			throw new MandatoryConstraintException(prop);
		}
		// Validate read-only constraint:
		if(_willUpdate()) {
			if(prop.isReadOnly()) {
				throw new ReadOnlyConstraintException(prop, value);
			}
		}
		// Validate auto-generated constraint:
		if(prop.isAutoGenerated()) {
			throw new AutoGenConstraintException(prop, value);
		}
		// Track update:
		_updates.put(prop,  value);
		// Check if the property is being set to its current value:
		_removeIfNotUpdated(prop, value);
	}

	public boolean hasUpdates() {
		return _updates.size() > 0;
	}
	
	public boolean isPersisted() {
		return _e.getKey().isComplete();
	}
	
	public QueryArg[] getNaturalKeyAsArg() {
		Property<?>[] props = getEntity().getNaturalKeyProperties();
		QueryArg[] arg = new QueryArg[props.length];
		for(int i=0; i<props.length; i++) {
			arg[i] = new QueryArg(props[i], getValue(props[i]));
		}
		return arg;
	}

	// For Entity:
	com.google.appengine.api.datastore.Entity getGoogleEntity() {
		return _e;
	}
	
	// For Entity:
	Set<Map.Entry<Property<?>,Object>> getUpdates() {
		return new HashMap<>(_updates).entrySet();
	}

	// For Entity:
	Set<Property<?>> getUpdatedProperties() {
		return new HashSet<>(_updates.keySet());
	}

	// For Entity:
	void updateFrom(Instance<?> i) {
		_updates.clear();
		_updates.putAll(i._updates);
		// Remove any redundant updates:
		for(Property<?> prop : i._updates.keySet()) {
			_removeIfNotUpdated(prop, _updates.get(prop));
		}
	}
	
	// For Entity:
	void flush(Property<?> prop, Object value) {
		value = prop.toPrimitive(value);
		if(prop.isIndexed()) {
			_e.setProperty(prop.getName(), value);
		}
		else {
			_e.setUnindexedProperty(prop.getName(), value);
		}
		// Its now safe to clear this update:
		_updates.remove(prop);
	}

	// For Entity:
	boolean isUpdated(Property<?> prop) {
		return _updates.containsKey(prop);
	}

	// For Self and Entity:
	void setGoogleEntity(com.google.appengine.api.datastore.Entity e) {
		_updates.clear();
		_e = e;
	}
	
	private void _checkProperty(Property<?> prop) {
		// Check if setting a property that pertains to this entity:
		if(!getEntity().getProperties().containsValue(prop)) {
			throw new IllegalArgumentException(x("property {} cannot be used in entity {}", prop.getFullName(), getEntity().getName()));
		}
	}

	private boolean _willUpdate() {
		return _e.getKey().isComplete();
	}
	
	private void _removeIfNotUpdated(Property<?> prop, Object value) {
		Object current = _e.getProperty(prop.getName());
		if(value == null) {
			if(current == null) {
				_updates.remove(prop);
			}
		}
		else {
			if(value.equals(current)) {
				_updates.remove(prop);
			}
		}
	}
}
