package unitTest;

import static org.junit.Assert.*;
import junit.framework.TestCase;

import org.junit.Test;

import data.TrainingRegime;

public class TrainingRegimeTest extends TestCase{
	
	private TrainingRegime tr = new TrainingRegime();
	
	@Test
	public void testCostToIncreaseAbilityUnder50Positive() {		
		assertEquals(0.1, tr.CostToIncreaseAbility(20), 1e-3);
	}	
	@Test
	public void testCostToIncreaseAbilityUnder50Negative() {
		assertNotSame(0.3, tr.CostToIncreaseAbility(20));
	}
	
	@Test
	public void testCostToIncreaseAbility50Under60Positive() {
		assertEquals(0.2, tr.CostToIncreaseAbility(50), 1e-3);
	}	
	@Test
	public void testCostToIncreaseAbility50Under60Negative() {
		assertNotSame(0.3, tr.CostToIncreaseAbility(50));
	}
	
	@Test
	public void testCostToIncreaseAbility60Under70Positive() {
		assertEquals(0.4, tr.CostToIncreaseAbility(60), 1e-3);
	}	
	@Test
	public void testCostToIncreaseAbility60Under70Negative() {
		assertNotSame(0.2, tr.CostToIncreaseAbility(60));
	}

	@Test
	public void testCostToIncreaseAbility70Positive() {
		assertEquals(0.6, tr.CostToIncreaseAbility(70), 1e-3);
	}	
	@Test
	public void testCostToIncreaseAbility70Negative() {
		assertNotSame(1, tr.CostToIncreaseAbility(70));
	}
}
