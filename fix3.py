with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    text = f.read()

# Replace the botched blocks
text = text.replace("""                    )
            },
                }
            modifier = Modifier.fillMaxSize()""", """                    )
                }
            },
            modifier = Modifier.fillMaxSize()""")

text = text.replace("""                        4 -> com.example.ui.DeveloperScreen()
                }
                    }""", """                        4 -> com.example.ui.DeveloperScreen()
                    }
                }""")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(text)
