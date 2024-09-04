# PLASTIC

This code contains java code of plastic and its ablations (CustomEFDT, CustomHT, EFHAT). The code for running our experiments and plotting can be found under [github.com/heymarco/CapyMOA-PLASTIC](https://github.com/heymarco/CapyMOA-PLASTIC)

Access the paper via [link.springer.com/chapter/10.1007/978-3-031-70362-1_3](https://link.springer.com/chapter/10.1007/978-3-031-70362-1_3)

## Abstract

Commonly used incremental decision trees for mining data streams include Hoeffding Trees (HT) and Extremely Fast Decision Trees (EFDT). EFDT exhibits faster learning than HT. However, due to its split revision procedure, EFDT suffers from sudden and unpredictable accuracy decreases caused by subtree pruning. To overcome this, we propose PLASTIC, an incremental decision tree that restructures the otherwise pruned subtree. This is possible due to *decision tree plasticity*: one can alter a tree's structure without affecting its predictions. We conduct extensive evaluations comparing PLASTIC with state-of-the-art methods on synthetic and real-world data streams. 
Our results show that PLASTIC improves EFDT's worst-case accuracy by up to 50 % and outperforms the current state of the art on real-world data. 
We provide an open-source implementation of PLASTIC within the MOA framework for mining high-speed data streams.

## Citing
If you want to cite this paper, use
```
@inproceedings{heyden2024leveraging,
  title={Leveraging Plasticity in Incremental Decision Trees},
  author={Heyden, Marco and Gomes, Heitor Murilo and Fouch{\'e}, Edouard and Pfahringer, Bernhard and B{\"o}hm, Klemens},
  booktitle={Joint European Conference on Machine Learning and Knowledge Discovery in Databases},
  pages={38--54},
  year={2024},
  organization={Springer}
}
```

# MOA (Massive Online Analysis)
[![Build Status](https://travis-ci.org/Waikato/moa.svg?branch=master)](https://travis-ci.org/Waikato/moa)
[![Maven Central](https://img.shields.io/maven-central/v/nz.ac.waikato.cms.moa/moa-pom.svg)](https://mvnrepository.com/artifact/nz.ac.waikato.cms)
[![DockerHub](https://img.shields.io/badge/docker-available-blue.svg?logo=docker)](https://hub.docker.com/r/waikato/moa)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

![MOA][logo]

[logo]: http://moa.cms.waikato.ac.nz/wp-content/uploads/2014/11/LogoMOA.jpg "Logo MOA"

MOA is the most popular open source framework for data stream mining, with a very active growing community ([blog](http://moa.cms.waikato.ac.nz/blog/)). It includes a collection of machine learning algorithms (classification, regression, clustering, outlier detection, concept drift detection and recommender systems) and tools for evaluation. Related to the WEKA project, MOA is also written in Java, while scaling to more demanding problems.

http://moa.cms.waikato.ac.nz/

## Using MOA

* [Getting Started](http://moa.cms.waikato.ac.nz/getting-started/)
* [Documentation](http://moa.cms.waikato.ac.nz/documentation/)
* [About MOA](http://moa.cms.waikato.ac.nz/details/)

MOA performs BIG DATA stream mining in real time, and large scale machine learning. MOA can be extended with new mining algorithms, and new stream generators or evaluation measures. The goal is to provide a benchmark suite for the stream mining community. 

## Mailing lists
* MOA users: http://groups.google.com/group/moa-users
* MOA developers: http://groups.google.com/group/moa-development

## Citing MOA
If you want to refer to MOA in a publication, please cite the following JMLR paper: 

> Albert Bifet, Geoff Holmes, Richard Kirkby, Bernhard Pfahringer (2010);
> MOA: Massive Online Analysis; Journal of Machine Learning Research 11: 1601-1604 


