float.sweeps <- as.vector(read.table("timing-float.txt",header=FALSE)[[1]])
double.sweeps <- as.vector(read.table("timing-double.txt",header=FALSE)[[1]])

float.minus.double.sweeps <- float.sweeps - double.sweeps

png("float-sweeps-minus-double-sweeps-differences.png",width=800,600)
hist(float.minus.double.sweeps,main="Histogram of extra sweeps in float version",xlab="Difference (float sweeps) - (double sweeps)",ylab="Frequency")
dev.off()
