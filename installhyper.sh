set -x
set -e

if [ ! -e hyper ]; then
	 mkdir hyper
	 cd hyper
	 wget https://hyper-install.s3.amazonaws.com/hyper-linux-x86_64.tar.gz
	 tar xvzf ./hyper-linux-x86_64.tar.gz
fi
