package test;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class Hamerator {
	public static void main(String[] args) throws AWTException {
		Robot robot = new Robot();
		robot.delay(2000);
		while(true) {
			robot.keyPress(KeyEvent.VK_A);
			robot.delay(200);
			robot.keyRelease(KeyEvent.VK_A);
			for(int i = 0; i < 4; i++) {
				robot.delay(200);
				robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
				robot.delay(50);
				robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			}
		}
	}
}
