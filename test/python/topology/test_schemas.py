# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2016
import unittest
import random
import collections
import sys

from streamsx.topology.schema import _SchemaParser
import streamsx.topology.schema as _sch
_PRIMITIVES = ['boolean', 'blob', 'int8', 'int16', 'int32', 'int64',
                 'uint8', 'uint16', 'uint32', 'uint64',
                 'float32', 'float64',
                 'complex32', 'complex64',
                 'timestamp', 'xml'
               ]

_COLLECTIONS = ['list', 'set']

def random_type(depth):
    r = random.random()
    if r < 0.10 and depth < 3:
        return random_schema(depth=depth)
    elif r < 0.2:
         c = 'map<'
         c += random_type(depth)
         c += ','
         c += random_type(depth)
         c += '>'
         return c
    elif r < 0.35:
         c = random.choice(_COLLECTIONS)
         c += '<'
         c += random_type(depth)
         c += '>'
         return c
    else:
        return random.choice(_PRIMITIVES)

def random_schema(depth=0):
    depth += 1
    s = 'tuple<'
    for an in range(random.randint(1, 30)):
        if an != 0:
            s += ','
        s += random_type(depth)
        s += " A_" + str(an)
    s += '>'
    return s

class TestSchema(unittest.TestCase):

    def test_simple(self):
      p = _SchemaParser('tuple<int32 a, int64 b>')
      p._parse()
      self.assertEqual(2, len(p._type))
      self.assertEqual('int32', p._type[0][0])
      self.assertEqual('a', p._type[0][1])

      self.assertEqual('int64', p._type[1][0])
      self.assertEqual('b', p._type[1][1])

    @unittest.skipIf(sys.version_info.major == 2, "subTest requires 3.5")
    def test_primitives(self):
      for typ in _PRIMITIVES:
          with self.subTest(typ = typ):
              p = _SchemaParser('tuple<' + typ + ' p>')
              p._parse()
              self.assertEqual(1, len(p._type))
              self.assertEqual(typ, p._type[0][0])
              self.assertEqual('p', p._type[0][1])

    @unittest.skipIf(sys.version_info.major == 2, "subTest requires 3.5")
    def test_collections(self):
      for ctyp in _COLLECTIONS:
          for etyp in _PRIMITIVES:
              typ = ctyp + '<' + etyp + '>'
              with self.subTest(typ = typ):
                  p = _SchemaParser('tuple<' + typ + ' c>')
                  p._parse()
                  self.assertEqual(1, len(p._type))
                  self.assertIsInstance(p._type[0][0], tuple)
                  self.assertEqual('c', p._type[0][1])

    def test_collections(self):
        typ = 'map<int32, complex64>'
        p = _SchemaParser('tuple<' + typ + ' m>')
        p._parse()

    def test_nested_tuple(self):
      p = _SchemaParser('tuple<int32 a, tuple<int64 b, complex32 c, float32 d> e>')
      p._parse()
      self.assertEqual(2, len(p._type))

      self.assertEqual('int32', p._type[0][0])
      self.assertEqual('a', p._type[0][1])

      self.assertIsInstance(p._type[1][0], tuple)
      self.assertEqual('e', p._type[1][1])
      nt = p._type[1][0]
      self.assertEqual('tuple', nt[0])
      self.assertIsInstance(nt[1], list)
      self.assertEqual(3, len(nt[1]))
      nttyp = nt[1]

      self.assertEqual('int64', nttyp[0][0])
      self.assertEqual('b', nttyp[0][1])
      self.assertEqual('complex32', nttyp[1][0])
      self.assertEqual('c', nttyp[1][1])
      self.assertEqual('float32', nttyp[2][0])
      self.assertEqual('d', nttyp[2][1])

    def test_random_schemas(self):
        """Just verify random schemas can be parsed"""
        for r in range(200):
            schema = random_schema()
            p = _SchemaParser(schema)
            p._parse()

    def test_named_schema(self):
        s = _sch.StreamSchema('tuple<int32 a, boolean alert>')

        nt1 = s._namedtuple()
        nt2 = s._namedtuple()
        self.assertIs(nt1, nt2)

        t = nt1(345, False)
        self.assertEqual(345, t.a)
        self.assertFalse(t.alert)
        self.assertEqual(345, t[0])
        self.assertFalse(t[1])
