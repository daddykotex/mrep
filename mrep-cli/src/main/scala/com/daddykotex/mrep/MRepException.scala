package com.daddykotex.mrep

final case class MRepException(msg: String) extends RuntimeException(msg)
