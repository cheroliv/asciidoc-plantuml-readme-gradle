@cucumber @readme @scaffold
Feature: Scaffold README configuration

  Background:
    Given a new ReadMe project

  # ── File creation ──────────────────────────────────────────────────────────

  Scenario: scaffoldReadme creates readme.yml when absent
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the following files should exist:
      | file                                     |
      | readme.yml                               |
      | .github/workflows/readme_action.yml      |

  # ── Default values ─────────────────────────────────────────────────────────

  Scenario: generated readme.yml contains expected source config
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the file "readme.yml" should contain the following yaml values:
      | key                  | value                                    |
      | source.dir           | .                                        |
      | source.defaultLang   | en                                       |

  Scenario: generated readme.yml contains expected output config
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the file "readme.yml" should contain the following yaml values:
      | key              | value                                    |
      | output.imgDir    | .github/workflows/readmes/images         |

  Scenario: generated readme.yml contains expected git config
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the file "readme.yml" should contain the following yaml values:
      | key                  | value                                                      |
      | git.userName         | github-actions[bot]                                        |
      | git.userEmail        | github-actions[bot]@users.noreply.github.com               |
      | git.commitMessage    | chore: generate readme [skip ci]                           |
      | git.token            | <YOUR_GITHUB_PAT>                                          |
    And the file "readme.yml" should contain the following watched branches:
      | branch  |
      | main    |
      | master  |

  # ── Idempotence ────────────────────────────────────────────────────────────

  Scenario: scaffoldReadme does not overwrite existing files
    Given the following files already exist:
      | file                                     | content               |
      | readme.yml                               | # existing config     |
      | .github/workflows/readme_action.yml      | # existing workflow   |
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the following files should still contain their original content:
      | file                                     | content               |
      | readme.yml                               | # existing config     |
      | .github/workflows/readme_action.yml      | # existing workflow   |

  # ── Fallback behavior ──────────────────────────────────────────────────────

  Scenario: scaffold falls back to "en" when defaultLang is empty
    Given a "readme.yml" with the following yaml values:
      | key                  | value |
      | source.defaultLang   |       |
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level  | keyword      | value  |
      | INFO   | defaultLang  | en     |

  Scenario: scaffold falls back to default imgDir when output.imgDir is empty
    Given a "readme.yml" with the following yaml values:
      | key            | value |
      | output.imgDir  |       |
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level  | keyword  | value                             |
      | INFO   | imgDir   | .github/workflows/readmes/images  |

  # ── Blocking validation ────────────────────────────────────────────────────

  Scenario: scaffold fails when source.dir does not exist
    Given a "readme.yml" with the following yaml values:
      | key         | value              |
      | source.dir  | /nonexistent/path  |
    When I am executing the task "scaffoldReadme"
    Then the build should fail
    And the build log should contain the following entries:
      | level  | keyword    | value       |
      | ERROR  | source.dir | nonexistent |

  Scenario: scaffold fails when output.imgDir cannot be created
    Given a "readme.yml" with the following yaml values:
      | key           | value                  |
      | output.imgDir | readme.yml/impossible  |
    When I am executing the task "scaffoldReadme"
    Then the build should fail
    And the build log should contain the following entries:
      | level | keyword | value   |
      | ERROR | imgDir  | created |

  Scenario: scaffold fails when output.imgDir exists but is not writable
    Given a "readme.yml" with the following yaml values:
      | key           | value                            |
      | output.imgDir | .github/workflows/readmes/images |
    And the directory ".github/workflows/readmes/images" exists and is not writable
    When I am executing the task "scaffoldReadme"
    Then the build should fail
    And the build log should contain the following entries:
      | level | keyword | value    |
      | ERROR | imgDir  | writable |

  # ── Git config warnings ────────────────────────────────────────────────────

  Scenario: scaffold warns when token is still a placeholder
    Given a "readme.yml" with the following yaml values:
      | key        | value              |
      | git.token  | <YOUR_GITHUB_PAT>  |
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level  | keyword  | value        |
      | WARN   | token    | placeholder  |

  Scenario: scaffold warns when GitHub is unreachable
    Given a "readme.yml" with the following yaml values:
      | key        | value        |
      | git.token  | ghp_valid    |
    And the git remote validator is mocked with result "UNREACHABLE"
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level  | keyword  | value        |
      | WARN   | GitHub   | unreachable  |

  Scenario: scaffold warns when repository does not exist
    Given a "readme.yml" with the following yaml values:
      | key        | value      |
      | git.token  | ghp_valid  |
    And the git remote validator is mocked with result "REPOSITORY_NOT_FOUND"
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level  | keyword     | value      |
      | WARN   | repository  | not found  |

  Scenario: scaffold warns when token has insufficient push rights
    Given a "readme.yml" with the following yaml values:
      | key        | value      |
      | git.token  | ghp_valid  |
    And the git remote validator is mocked with result "INSUFFICIENT_PUSH_RIGHTS"
    When I am executing the task "scaffoldReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level  | keyword  | value               |
      | WARN   | push     | insufficient rights |

  # ── Integration ────────────────────────────────────────────────────────────

  Scenario: processReadme succeeds after scaffold with invalid git config
    Given a "readme.yml" with the following yaml values:
      | key        | value             |
      | git.token  | <YOUR_GITHUB_PAT> |
    And the git remote validator is mocked with result "TOKEN_PLACEHOLDER"
    And the file "README_truth.adoc" exists with content "= Hello"
    When I am executing the task "processReadme"
    Then the build should succeed
    And the following files should exist:
      | file         |
      | README.adoc  |
