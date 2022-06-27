/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.pdfbox.pdmodel.graphics.shading;

import android.graphics.PointF;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tom_roush.harmony.awt.geom.AffineTransform;
import com.tom_roush.harmony.javax.imageio.stream.ImageInputStream;
import com.tom_roush.harmony.javax.imageio.stream.MemoryCacheImageInputStream;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRange;
import com.tom_roush.pdfbox.util.Matrix;

/**
 * Resources for a shading type 5 (Lattice-Form Gouraud-Shade Triangle Mesh).
 */
public class PDShadingType5 extends PDTriangleBasedShadingType
{
    /**
     * Constructor using the given shading dictionary.
     *
     * @param shadingDictionary the dictionary for this shading
     */
    public PDShadingType5(COSDictionary shadingDictionary)
    {
        super(shadingDictionary);
    }

    @Override
    public int getShadingType()
    {
        return PDShading.SHADING_TYPE5;
    }

    /**
     * The vertices per row of this shading. This will return -1 if one has not
     * been set.
     *
     * @return the number of vertices per row
     */
    public int getVerticesPerRow()
    {
        return getCOSObject().getInt(COSName.VERTICES_PER_ROW, -1);
    }

    /**
     * Set the number of vertices per row.
     *
     * @param verticesPerRow the number of vertices per row
     */
    public void setVerticesPerRow(int verticesPerRow)
    {
        getCOSObject().setInt(COSName.VERTICES_PER_ROW, verticesPerRow);
    }

//    public Paint toPaint(Matrix matrix) TODO: PdfBox-Android

    @SuppressWarnings("squid:S1166")
    @Override
    List<ShadedTriangle> collectTriangles(AffineTransform xform, Matrix matrix) throws IOException
    {
        COSDictionary dict = getCOSObject();
        if (!(dict instanceof COSStream))
        {
            return Collections.emptyList();
        }
        PDRange rangeX = getDecodeForParameter(0);
        PDRange rangeY = getDecodeForParameter(1);
        if (Float.compare(rangeX.getMin(), rangeX.getMax()) == 0 ||
            Float.compare(rangeY.getMin(), rangeY.getMax()) == 0)
        {
            return Collections.emptyList();
        }
        int numPerRow = getVerticesPerRow();
        PDRange[] colRange = new PDRange[getNumberOfColorComponents()];
        for (int i = 0; i < colRange.length; ++i)
        {
            colRange[i] = getDecodeForParameter(2 + i);
        }
        List<Vertex> vlist = new ArrayList<Vertex>();
        long maxSrcCoord = (long) Math.pow(2, getBitsPerCoordinate()) - 1;
        long maxSrcColor = (long) Math.pow(2, getBitsPerComponent()) - 1;
        COSStream cosStream = (COSStream) dict;

        ImageInputStream mciis = new MemoryCacheImageInputStream(cosStream.createInputStream());
        try
        {
            boolean eof = false;
            while (!eof)
            {
                Vertex p;
                try
                {
                    p = readVertex(mciis, maxSrcCoord, maxSrcColor, rangeX, rangeY, colRange, matrix, xform);
                    vlist.add(p);
                }
                catch (EOFException ex)
                {
                    eof = true;
                }
            }
        }
        finally
        {
            mciis.close();
        }
        int rowNum = vlist.size() / numPerRow;
        Vertex[][] latticeArray = new Vertex[rowNum][numPerRow];
        List<ShadedTriangle> list = new ArrayList<ShadedTriangle>();
        if (rowNum < 2)
        {
            // must have at least two rows; if not, return empty list
            return list;
        }
        for (int i = 0; i < rowNum; i++)
        {
            for (int j = 0; j < numPerRow; j++)
            {
                latticeArray[i][j] = vlist.get(i * numPerRow + j);
            }
        }

        PointF[] ps = new PointF[3]; // array will be shallow-cloned in ShadedTriangle constructor
        float[][] cs = new float[3][];
        for (int i = 0; i < rowNum - 1; i++)
        {
            for (int j = 0; j < numPerRow - 1; j++)
            {
                ps[0] = latticeArray[i][j].point;
                ps[1] = latticeArray[i][j + 1].point;
                ps[2] = latticeArray[i + 1][j].point;

                cs[0] = latticeArray[i][j].color;
                cs[1] = latticeArray[i][j + 1].color;
                cs[2] = latticeArray[i + 1][j].color;

                list.add(new ShadedTriangle(ps, cs));

                ps[0] = latticeArray[i][j + 1].point;
                ps[1] = latticeArray[i + 1][j].point;
                ps[2] = latticeArray[i + 1][j + 1].point;

                cs[0] = latticeArray[i][j + 1].color;
                cs[1] = latticeArray[i + 1][j].color;
                cs[2] = latticeArray[i + 1][j + 1].color;

                list.add(new ShadedTriangle(ps, cs));
            }
        }
        return list;
    }
}
