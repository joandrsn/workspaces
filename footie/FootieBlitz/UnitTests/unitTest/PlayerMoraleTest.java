package unitTest;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import junit.framework.TestCase;
import model.Hjaelper;

import org.junit.Test;

import data.PlayerMorale;


public class PlayerMoraleTest extends TestCase{

	
/////////////////////////////////////////////////////	
////////TEST calculateAverageMatchRating(ratingsSum, matchesCount)////////	
/////////////////////////////////////////////////////
	@Test
	public void calculateAverageMatchRating120Rating60MatchesPositive() {
		assertEquals(2.0, Hjaelper.calculateAverageMatchRating(120, 60), 1e-3);		
	}
	@Test
	public void calculateAverageMatchRating120Rating60MatchesNegative() {
		assertThat(Hjaelper.calculateAverageMatchRating(120, 60), is(not(2.1)));
		assertThat(Hjaelper.calculateAverageMatchRating(120, 60), is(not(1.9)));
	}
		
	@Test
	public void calculateAverageMatchRating120Rating30MatchesPositive() {
		assertEquals(2.5, Hjaelper.calculateAverageMatchRating(120, 30), 1e-3);		
	}
	
	@Test
	public void calculateAverageMatchRating180Rating65MatchesPositive() {
		assertEquals(3, Hjaelper.calculateAverageMatchRating(180, 65), 1e-3);		
	}

/////////////////////////////////////////////////////
////////TEST getMoraleRate(morale)////////	
/////////////////////////////////////////////////////
	@Test
	public void testGetMoraleRateMorale10(){
		assertEquals(0.21, PlayerMorale.getMoraleRate(10), 1e-3);		
	}
	
	@Test
	public void testGetMoraleRateMorale30(){
		assertEquals(0.25, PlayerMorale.getMoraleRate(30), 1e-3);		
	}
	
	@Test
	public void testGetMoraleRateMorale40(){
		assertEquals(0.55, PlayerMorale.getMoraleRate(40), 1e-3);		
	}
	
	@Test
	public void testGetMoraleRateMorale50(){
		assertEquals(1.0, PlayerMorale.getMoraleRate(50), 1e-3);		
	}
	
	@Test
	public void testGetMoraleRateMorale60(){
		assertEquals(1.64, PlayerMorale.getMoraleRate(60), 1e-3);		
	}
	
	@Test
	public void testGetMoraleRateMorale70(){
		assertEquals(2.48, PlayerMorale.getMoraleRate(70), 1e-3);		
	}
	
	@Test
	public void testGetMoraleRateMorale80(){
		assertEquals(3.56, PlayerMorale.getMoraleRate(80), 1e-3);		
	}
	
	@Test
	public void testGetMoraleRateMorale90(){
		assertEquals(4.89, PlayerMorale.getMoraleRate(90), 1e-3);		
	}
	
/////////////////////////////////////////////////////
////////TEST calcNewMorale(morale, change)////////	
/////////////////////////////////////////////////////	
	@Test
	public void testCalcNewMorale10Change1(){
		assertEquals(14.76, PlayerMorale.calcNewMorale(10, 1), 1e-3);		
	}
	@Test
	public void testCalcNewMorale10ChangeNeg1(){
		assertEquals(9.79, PlayerMorale.calcNewMorale(10, -1), 1e-3);		
	}
	
	@Test
	public void testCalcNewMorale10Change5(){
		assertEquals(33.81, PlayerMorale.calcNewMorale(10, 5), 1e-3);		
	}	
	@Test
	public void testCalcNewMorale10ChangeNeg5(){
		assertEquals(8.95, PlayerMorale.calcNewMorale(10, -5), 1e-3);		
	}
	
	@Test
	public void testCalcNewMorale30Change1(){
		assertEquals(34.0, PlayerMorale.calcNewMorale(30, 1), 1e-3);		
	}
	@Test
	public void testCalcNewMorale30ChangeNeg1(){
		assertEquals(29.75, PlayerMorale.calcNewMorale(30, -1), 1e-3);		
	}
	
	@Test
	public void testCalcNewMorale30Change5(){
		assertEquals(45.23, PlayerMorale.calcNewMorale(30, 5), 1e-3);		
	}	
	@Test
	public void testCalcNewMorale30ChangeNeg5(){
		assertEquals(28.77, PlayerMorale.calcNewMorale(30, -5), 1e-3);		
	}
	
	@Test
	public void testCalcNewMorale60Change1(){
		assertEquals(60.61, PlayerMorale.calcNewMorale(60, 1), 1e-3);		
	}
	@Test
	public void testCalcNewMorale60ChangeNeg1(){
		assertEquals(58.36, PlayerMorale.calcNewMorale(60, -1), 1e-3);		
	}
	
	@Test
	public void testCalcNewMorale60Change5(){
		assertEquals(62.96, PlayerMorale.calcNewMorale(60, 5), 1e-3);		
	}	
	@Test
	public void testCalcNewMorale60ChangeNeg5(){
		assertEquals(52.48, PlayerMorale.calcNewMorale(60, -5), 1e-3);		
	}
	
}
