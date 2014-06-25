/**
 Copyright 2014 Mikhail Shugay (mikhail.shugay@gmail.com)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.antigenomics.vdjtools.sample

import com.antigenomics.vdjtools.Clonotype
import com.antigenomics.vdjtools.Software

class SampleCollection {
    final Map<String, Sample> sampleMap = new HashMap<>()
    final List<String> metadataHeader = new ArrayList<>()
    final Software software

    SampleCollection(String sampleMetadataFileName, Software software) {
        this.software = software
        new File(sampleMetadataFileName).withReader { reader ->
            metadataHeader.addAll(reader.readLine().split("\t")[2..-1])

            def line, splitLine
            while ((line = reader.readLine()) != null) {
                splitLine = line.split("\t")
                String fileName = splitLine[0], sampleId = splitLine[1]
                def entries = splitLine[2..-1]

                if (entries.size() != metadataHeader.size())
                    throw new Exception("Different number of entries in metadata header and sample $sampleId")

                def sample = sampleMap[sampleId]
                if (!sample)
                    sampleMap.put(sampleId,
                            new Sample(new SampleMetadata(sampleId, entries),
                                    loadData(fileName)))
                else {
                    sample.clonotypes.addAll(loadData(fileName))
                }
            }
        }
    }

    List<Clonotype> loadData(String fileName) {
        def clonotypes = new ArrayList()
        new File(fileName).withReader { reader ->
            for (int i = 0; i < software.headerLineCount; i++)
                reader.readLine()

            def line
            while ((line = reader.readLine()) != null) {
                if (!software.comment || !line.startsWith(software.comment))
                    clonotypes.add(Clonotype.parseClonotype(line, software))
            }
        }
        clonotypes
    }

    Collection<SamplePair> listPairs() {
        def samplePairs = new LinkedList()
        for (int i = 0; i < sampleMap.values().size(); i++)
            for (int j = i + 1; j < sampleMap.values().size(); j++)
                samplePairs.add(new SamplePair(sampleMap.values()[i], sampleMap.values()[j]))
        samplePairs
    }
}