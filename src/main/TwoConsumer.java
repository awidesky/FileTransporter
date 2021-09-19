package main;

@FunctionalInterface
public interface TwoConsumer<T,  U> {

	public void consume(T t, U u);
	
}
