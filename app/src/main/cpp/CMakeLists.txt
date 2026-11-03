# Benflix game player (see benflix_retro.c). The emulators themselves are downloaded separately.
cmake_minimum_required(VERSION 3.22.1)
project(benflixretro C)

add_library(benflixretro SHARED benflix_retro.c)
target_compile_options(benflixretro PRIVATE -O2 -Wall)
target_link_libraries(benflixretro android log GLESv3 EGL dl)
