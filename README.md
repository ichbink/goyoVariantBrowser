# goyoVariantBrowser

A full stack web app for browsing, filtering and prioritize genomics variants from massively parallel sequence -WGS, WES, panels. 

Implemented in Scala, it uses Play Framework and Apache Kakfa streams for backend and ScalaJS in client side.

Starting from a tabular file with genomics coordinates of variants, GoyoVariantBrowser send it to Kakfa brokers. Then variants are reviewed from client browser showing pass/filtered tabs.
Users are able to setup different filtering strategies and generating subsets from those filters for a refined rewiew.


Work in progress!!!
