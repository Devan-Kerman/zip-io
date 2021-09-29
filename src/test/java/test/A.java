package test;

import java.nio.ByteBuffer;
import java.util.Base64;

public class A {
	public static void main(String[] args) {
		int i = 38782;
		for(int i1 = 0; i1 < i; i1++) {
			i /= 64;
			if(i == 0) {
				System.out.println(i1);
			}
		}
		System.out.println("38782");
		System.out.println(Integer.toString(38782, 16));
		System.out.println(Integer.toString(38782, 32));
		System.out.println(Base64.getEncoder().encodeToString(ByteBuffer.allocate(4).putInt(38782).array()));
	}
}
