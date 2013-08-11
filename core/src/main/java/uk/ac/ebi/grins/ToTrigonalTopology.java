/*
 * Copyright (c) 2013, European Bioinformatics Institute (EMBL-EBI)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the FreeBSD Project.
 */

package uk.ac.ebi.grins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Convert direction (up/down) bonds to trigonal topology (double bond atom
 * centric stereo specification).
 *
 * <blockquote><pre>
 *    F/C=C/F -> F/[C@H]=[C@H]F
 *    F/C=C\F -> F/[C@H]=[C@@H]F
 *    F\C=C/F -> F/[C@@H]=[C@H]F
 *    F\C=C\F -> F/[C@@H]=[C@@H]F
 * </pre></blockquote>
 *
 * @author John May
 */
final class ToTrigonalTopology {

    public ChemicalGraph transform(ChemicalGraph g) {

        ChemicalGraph h = new ChemicalGraph(g.order());

        // original topology information this is unchanged
        for (int u = 0; u < g.order(); u++) {
            h.addTopology(g.topologyOf(u));
        }

        Map<Edge, Edge> replacements = new HashMap<Edge, Edge>();

        // change edges (only changed added to replacement)
        for (int u = 0; u < g.order(); u++) {
            for (final Edge e : g.edges(u)) {
                if (e.other(u) > u && e.bond() == Bond.UP || e
                        .bond() == Bond.DOWN) {
                    replacements.put(e,
                                     new Edge(u, e.other(u), Bond.IMPLICIT));
                }
            }
        }


        List<Edge> es = doubleBondLabelledEdges(g);


        for (Edge e : es) {
            int u = e.either();
            int v = e.other(u);

            // add to topologies
            h.addTopology(toTrigonal(g, e, u));
            h.addTopology(toTrigonal(g, e, v));
        }

        for (int u = 0; u < g.order(); u++) {
            Atom a = g.atom(u);
            if (a.subset() && h.topologyOf(u) != Topology.unknown()) {
                h.addAtom(asBracketAtom(u, g));
            } else {
                h.addAtom(a);
            }
        }

        // append the edges, replacing any which need to be changed
        for (int u = 0; u < g.order(); u++) {
            for (Edge e : g.edges(u)) {
                if (e.other(u) > u) {
                    Edge replacement = replacements.get(e);
                    if (replacement != null)
                        e = replacement;
                    h.addEdge(e);
                }
            }
        }

        return h;
    }

    private Atom asBracketAtom(int u, ChemicalGraph g) {
        Atom a = g.atom(u);
        int nElectrons = 0;
        for (Edge e : g.edges(u)) {
            nElectrons += e.bond().electrons();
        }
        return new Atom.BracketAtom(-1,
                                    a.element(),
                                    a.element()
                                     .implicitHydrogens(nElectrons / 2),
                                    0,
                                    0,
                                    a.aromatic());
    }

    private Topology toTrigonal(ChemicalGraph g, Edge e, int u) {

        List<Edge> es = g.edges(u);
        int offset = es.indexOf(e);

        int parity = 0;

        // vertex information for topology
        int j = 0;
        int[] vs = new int[3];

        vs[j++] = e.other(u);

        for (int i = 1; i < es.size(); i++) {
            Edge f = es.get((i + offset) % es.size());
            switch (f.bond(u)) {
                case UP:
                    if (i == 2)
                        parity = -1;
                    else
                        parity = 1;
                    if (f.other(u) < u)
                        parity *= -1;
                    break;
                case DOWN:
                    if (i == 2)
                        parity = 1;
                    else
                        parity = -1;
                    if (f.other(u) < u)
                        parity *= -1;
                    break;
            }

            vs[j++] = f.other(u);
        }

        if (j < 3)
            vs[j] = u;

        if (parity == 0)
            return Topology.unknown();

        Configuration c = parity > 0 ? Configuration.DB1
                                     : Configuration.DB2;

        return Topology.trigonal(u, vs, c);
    }

    private List<Edge> doubleBondLabelledEdges(ChemicalGraph g) {
        List<Edge> es = new ArrayList<Edge>();
        for (int u = 0; u < g.order(); u++) {
            for (Edge e : g.edges(u)) {
                if (e.other(u) > u && e.bond() == Bond.DOUBLE) {
                    es.add(e);
                }
            }
        }
        return es;
    }

}