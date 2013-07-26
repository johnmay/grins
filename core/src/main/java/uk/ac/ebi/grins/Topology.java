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

import java.util.Arrays;

import static uk.ac.ebi.grins.Configuration.AL1;
import static uk.ac.ebi.grins.Configuration.AL2;
import static uk.ac.ebi.grins.Configuration.ANTI_CLOCKWISE;
import static uk.ac.ebi.grins.Configuration.CLOCKWISE;
import static uk.ac.ebi.grins.Configuration.OH1;
import static uk.ac.ebi.grins.Configuration.OH2;
import static uk.ac.ebi.grins.Configuration.TB1;
import static uk.ac.ebi.grins.Configuration.TB2;
import static uk.ac.ebi.grins.Configuration.TH1;
import static uk.ac.ebi.grins.Configuration.TH2;
import static uk.ac.ebi.grins.Configuration.Type.Implicit;
import static uk.ac.ebi.grins.Configuration.Type.Tetrahedral;

/**
 * Defines the relative topology around a vertex (atom).
 *
 * @author John May
 */
abstract class Topology {

    /**
     * The vertex/atom which this topology describes.
     *
     * @return vertex
     * @throws IllegalArgumentException unknown topology
     */
    abstract int atom();

    /**
     * The configuration of the topology.
     *
     * @return configuration for this topology
     */
    abstract Configuration configuration();

    /**
     * Arrange the topology relative to a given ranking of vertices.
     *
     * @param rank ordering of vertices
     * @return a new topology with the neighbors arranged by the given rank
     */
    abstract Topology orderBy(int[] rank);

    /**
     * Transform the topology to one with the given {@literal mapping}.
     *
     * @param mapping the mapping used to transform the topology
     * @return a new topology with it's vertices mapped
     */
    abstract Topology transform(int[] mapping);

    /**
     * Compute the permutation parity of the vertices {@literal vs} for the
     * given {@literal rank}. The parity defines the oddness or evenness of a
     * permutation and is the number of inversions (swaps) one would need to
     * make to place the 'vs' in the order specified by rank.
     *
     * @param vs   array of vertices
     * @param rank rank of vertices, |R| = max(vs) + 1
     * @return sign of the permutation, -1=odd or 1=even
     * @see <a href="http://en.wikipedia.org/wiki/Parity_of_a_permutation>Parity
     *      of a Permutation</a>
     */
    static int parity(int[] vs, int[] rank) {
        // count elements which are out of order and by how much
        int count = 0;
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                if (rank[vs[i]] > rank[vs[j]])
                    count++;
            }
        }
        // odd parity = -1, even parity = 1
        return (count & 0x1) == 1 ? -1 : 1;
    }

    /**
     * Sorts the array {@literal vs} into the order given by the {@literal
     * rank}.
     *
     * @param vs   vertices to sort
     * @param rank rank of vertices
     * @return sorted array (cpy of vs)
     */
    static int[] sort(int[] vs, int[] rank) {
        int[] ws = Arrays.copyOf(vs, vs.length);

        // insertion sort using rank for the ordering
        for (int i = 0, j = i; i < vs.length - 1; j = ++i) {
            int v = ws[i + 1];
            while (rank[v] < rank[ws[j]]) {
                ws[j + 1] = ws[j];
                if (--j < 0)
                    break;
            }
            ws[j + 1] = v;
        }
        return ws;
    }

    /**
     * Specify unknown configuration on atom - there is no vertex data stored.
     *
     * @return unknown topology
     */
    static Topology unknown() {
        return UNKNOWN;
    }

    /**
     * Define tetrahedral topology of the given configuration.
     *
     * @param u             central atom
     * @param vs            vertices surrounding u, the first is the vertex we
     *                      are looking from
     * @param configuration the tetrahedral configuration, @TH1, @TH2, @ or @@
     * @return topology instance for that configuration
     * @see Configuration
     */
    static Topology tetrahedral(int u, int[] vs, Configuration configuration) {

        if (configuration.type() != Implicit
                && configuration.type() != Tetrahedral)
            throw new IllegalArgumentException(configuration.type()
                                                       + "invalid tetrahedral configuration");

        int p = configuration.shorthand() == CLOCKWISE ? 1 : -1;

        return new Tetrahedral(u,
                               Arrays.copyOf(vs, vs.length),
                               p);
    }

    /**
     * Convert an implicit configuration ('@' or '@@') c, to an explicit one
     * (e.g. @TH1).
     *
     * @param c
     * @param g
     * @param u
     * @return
     */
    static Configuration toExplicit(ChemicalGraph g, int u, Configuration c) {

        // already explicit
        if (c.type() != Implicit)
            return c;

        int deg = g.degree(u);
        int valence = deg + g.atom(u).hydrogens();

        // tetrahedral topology, square planar must always be explicit
        if (valence == 4) {
            return c == ANTI_CLOCKWISE ? TH1 : TH2;
        }

        // tetrahedral topology with implicit lone pair or double bond (Sp2)
        // atoms (todo)
        else if (valence == 3) {

            // XXX: sulfoxide special case... would be better to compute
            // hybridization don't really like doing this here but it works
            if (g.atom(u).element() == Element.Sulfur) {
                for (Edge e : g.edges(u)) {
                    if (e.bond() == Bond.DOUBLE
                            && g.atom(e.other(u)).element() == Element.Oxygen) {
                        return c == ANTI_CLOCKWISE ? TH1 : TH2;
                    }
                }
            }

            // TODO: double bond stereo as atom specification

        }

        // odd number of cumulated double bond systems (e.g. allene)
        else if (deg == 2) {
            // check both bonds are double
            for (Edge e : g.edges(u))
                if (e.bond() != Bond.DOUBLE)
                    return Configuration.UNKNOWN;
            return c == ANTI_CLOCKWISE ? AL1 : AL2;
        }

        // trigonal bipyramidal
        else if (valence == 5) {
            return c == ANTI_CLOCKWISE ? TB1 : TB2;
        }

        // octahedral
        else if (valence == 6) {
            return c == ANTI_CLOCKWISE ? OH1 : OH2;
        }

        return Configuration.UNKNOWN;
    }

    private static Topology UNKNOWN = new Topology() {
        @Override int atom() {
            throw new IllegalArgumentException("unknown topology");
        }

        @Override Configuration configuration() {
            return Configuration.UNKNOWN;
        }

        @Override Topology orderBy(int[] rank) {
            return this;
        }

        @Override Topology transform(int[] mapping) {
            return this;
        }
    };

    private static final class Tetrahedral extends Topology {

        private final int   u;
        private final int[] vs;
        private final int   p;

        private Tetrahedral(int u, int[] vs, int p) {
            this.u = u;
            this.vs = vs;
            this.p = p;
        }

        /** @inheritDoc */
        @Override int atom() {
            return u;
        }

        /** @inheritDoc */
        @Override Configuration configuration() {
            return p < 0 ? Configuration.TH1 : Configuration.TH2;
        }

        /** @inheritDoc */
        @Override Topology orderBy(int[] rank) {
            int q = p * parity(vs, rank);

            // consider implicit hydrogen position
            if (vs.length == 3) {
                int count = 0;
                for (int v : vs)
                    if (rank[v] > rank[u])
                        count++;
                // (-1)^n
                q *= (count & 0x1) == 1 ? -1 : 1;
            }

            return new Tetrahedral(u,
                                   sort(vs, rank),
                                   q);
        }

        /** @inheritDoc */
        @Override Topology transform(final int[] mapping) {
            int[] ws = new int[vs.length];
            for (int i = 0; i < vs.length; i++)
                ws[i] = mapping[vs[i]];
            return new Tetrahedral(mapping[u], ws, p);
        }
    }
}
