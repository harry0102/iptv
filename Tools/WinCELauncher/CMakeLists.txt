SET(WinCELauncher_INCLUDE_DIRECTORIES
    "${WEBKIT_DIR}/wince"
    "${WEBCORE_DIR}"
    "${WEBCORE_DIR}/platform"
    "${WEBCORE_DIR}/platform/graphics"
    "${WEBCORE_DIR}/platform/network"
    "${WEBCORE_DIR}/platform/text"
    "${JAVASCRIPTCORE_DIR}"
    "${WTF_DIR}"
    "${DERIVED_SOURCES_DIR}"
    "${CMAKE_BINARY_DIR}"
)

SET(WinCELauncher_SOURCES
    ${TOOLS_DIR}/WinCELauncher/main.cpp
)

SET(WinCELauncher_LIBRARIES
    ${WebKit_LIBRARY_NAME}
)

INCLUDE_DIRECTORIES(${WinCELauncher_INCLUDE_DIRECTORIES})
ADD_EXECUTABLE(WinCELauncher ${WinCELauncher_SOURCES})
ADD_DEPENDENCIES(WinCELauncher ${WebKit_LIBRARY_NAME})
TARGET_LINK_LIBRARIES(WinCELauncher ${WinCELauncher_LIBRARIES})
SET_TARGET_PROPERTIES(WinCELauncher PROPERTIES FOLDER "Tools")
SET_TARGET_PROPERTIES(WinCELauncher PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}")
