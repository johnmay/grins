package uk.ac.ebi.grins;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/** @author John May */
public class BondBasedConfigurationTest {

    @Test(expected = IllegalArgumentException.class)
    public void nonDoubleBond() throws Exception {
        ChemicalGraph g = ChemicalGraph.fromSmiles("CCCC");
        BondBasedConfiguration.configurationOf(g, 0, 1, 2, 3);
    }

    @Test
    public void opposite1() throws Exception {
        ChemicalGraph g = ChemicalGraph.fromSmiles("F/C=C/F");
        assertThat(BondBasedConfiguration.configurationOf(g, 0, 1, 2, 3),
                   is(Configuration.DoubleBond.OPPOSITE));
    }

    @Test
    public void opposite2() throws Exception {
        ChemicalGraph g = ChemicalGraph.fromSmiles("F\\C=C\\F");
        assertThat(BondBasedConfiguration.configurationOf(g, 0, 1, 2, 3),
                   is(Configuration.DoubleBond.OPPOSITE));
    }

    @Test
      public void together1() throws Exception {
        ChemicalGraph g = ChemicalGraph.fromSmiles("F/C=C\\F");
        assertThat(BondBasedConfiguration.configurationOf(g, 0, 1, 2, 3),
                   is(Configuration.DoubleBond.TOGETHER));
    }

    @Test
    public void together2() throws Exception {
        ChemicalGraph g = ChemicalGraph.fromSmiles("F\\C=C/F");
        assertThat(BondBasedConfiguration.configurationOf(g, 0, 1, 2, 3),
                   is(Configuration.DoubleBond.TOGETHER));
    }
}