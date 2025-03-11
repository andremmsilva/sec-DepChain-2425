package depchain.events;

public abstract class Event<T> {
    public T arg;

    public Event() {
        this.arg = null;
    }

    public Event(T arg) {
        this.arg = arg;
    }
}
